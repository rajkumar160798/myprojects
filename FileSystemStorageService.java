package com.cvs.anbc.ahreports.storage;

 
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

 
import com.cvs.anbc.ahreports.dao.StorageProperties;
import com.cvs.anbc.ahreports.exceptions.StorageException;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FormatOptions;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LoadJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

 
/**
 * Service class to handle file storage and upload operations to Google Cloud
 * Storage(GCS bucket).
 */
@Service
public class FileSystemStorageService implements StorageService {

 
    private static final Logger logger = LoggerFactory.getLogger(FileSystemStorageService.class);

 
    private final Storage storage; // GCS instance
    private final String bucketName; // Name of the GCS bucket where files are uploaded
    private final BigQuery bigQuery;
    private final String projectId;
    //BigQuery compute project Id
    @Value("${bigquery.compute.projectId}")
    private String computeProjectId;

 
    /**
     * Constructor initializes the GCS Client and Bucket where we upload the file.
     *
     * @param properties Configuration object contains details of Gcs bucket.
     * @throws StorageException if bucket name is empty.
     */
    public FileSystemStorageService(StorageProperties properties) {
        // Validates that the bucket name is not empty
        if (properties.getBucketName().trim().length() == 0) {
            throw new StorageException("GCS bucket name cannot be empty.");
        }
        if (properties.getProjectId() == null) {
            throw new StorageException("BigQuery project Id cannot be empty");
        }
       

 
        // Initializes the GCS storage
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bigQuery = BigQueryOptions.getDefaultInstance().getService();
        this.bucketName = properties.getBucketName();
        this.projectId = properties.getProjectId();
       
        logger.info(" The Project iD is : " + this.projectId);
        logger.info(" FileSystemStorageService initialized with bucket: " + this.bucketName);

 
    }

 
    /**
     * Stores the file in temp directory and then uploads it to the GCS bucket.
     *
     * @param file the uploaded file recieved.
     * @throws StorageException if the file is empty or upload to the GCS bucket
     *                          fails.
     *                          }
     */
    @Override
    public void store(MultipartFile file) {
        Path tempDir = null; // tempDir for storing file
        Path tempFilePath = null; // Path to the temporary file

 
        try {
            // Checks if the file is empty
            logger.info("Starting the store process...");
            if (file.isEmpty()) {
                throw new StorageException("failed to store empty file.");
            }

 
            // creates a temp directory to hold the file.
            tempDir = Files.createTempDirectory("upload-temp");
            // Resolves the path of temp file.
            tempFilePath = tempDir.resolve(file.getOriginalFilename());

 
            // copies the file's input stream to the temp file
            Files.copy(file.getInputStream(), tempFilePath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File saved to temp directory: {}", tempFilePath);

 
            // Preapres the metadata for the GCS object
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, file.getOriginalFilename()).build();

 
            // Upload the File to GCS
            storage.create(blobInfo, Files.readAllBytes(tempFilePath));
            logger.info("File uploaded to GCS bucket:{}/{}", bucketName, file.getOriginalFilename());

 
        } catch (IOException e) {
            // Logs the error and throws a StorageException
            e.printStackTrace();
            logger.error("Error during the file upload:{} ", e.getMessage(), e);
            // handles Exception during file operations or GCS upload.
            throw new StorageException("Failed to upload file to GCS bucket", e);

 
        } finally {
            // Cleans up temporary files and directories
            try {
                if (tempFilePath != null && Files.exists(tempFilePath)) {
                    Files.delete(tempFilePath);
                }
                if (tempDir != null && Files.exists(tempDir)) {
                    Files.delete(tempDir);
                }
                logger.info("Temporary files cleaned up.");
            } catch (IOException e) {
                // Logs the error and continues.
                e.printStackTrace();
                logger.warn("failed to clean up temp files: {}", e.getMessage(), e);
            }
        }
    }

 
    @Override
    public void createOrReplaceBigQueryTable(String fileName, String datasetName, String tableName) {
        logger.info("Starting BigQuery table creation/replacement....");

 
        String gcsFilePath = "gs://" + bucketName + "/" + fileName;
        logger.info("GCS File Path: {}", gcsFilePath);
        logger.info("Project ID = {}", projectId);

 
        TableId tableId = TableId.of(projectId, datasetName, tableName);
        logger.info("Table ID: = {}", tableId.toString());

 
        LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(tableId, gcsFilePath)
                .setFormatOptions(FormatOptions.csv())
                .setAutodetect(true)
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
                .build();

 
        try {
            // The location and job name are optional,
            // if both are not specified then client will auto-create.
            // However, we have to specify the Compute project name explicitly per the environment.
            // Because the GKE is tied with Storage project and our SA is not having access to BigQuery job execution.
            // only from the Compute project
            String jobName = "jobId_" + UUID.randomUUID().toString();
            JobId jobId = JobId.newBuilder().setLocation("us").setJob(jobName).setProject(computeProjectId)
                    .build();

 
            logger.info("Compute Project ID : {}", computeProjectId);

 
            logger.info("Submitting Bigquery job for table creation: {}", tableName);
            Job job = bigQuery.create(JobInfo.of(jobId, loadConfig));
            job = job.waitFor();

 
            if (job.isDone()) {
                logger.info("Table created/replaced succesfully:{}.{}.{}", projectId, datasetName, tableName);
            } else {
                logger.error("Data load oeration failed", job.getStatus().getError());
                // throw new RuntimeException("BigQuery create table job failed: " +
                // job.getStatus().getError());
                throw new RuntimeException("Bigquery create table job failed:" + job.getStatus().getError());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("BigQuery job was interrupted", e);
        }
    }

 
    @Override
    public void createOrReplaceBigQueryTableWithColumns(String fileName, String datasetName, String tableName,
            List<String> selectedColumns) {
        String gcsFilePath = "gs://" + bucketName + "/" + fileName;
        logger.info("GCS File Path: {}", gcsFilePath);
        logger.info("Project ID = {}", projectId);

 
        TableId tableId = TableId.of(projectId, datasetName, tableName);
        logger.info("Table ID: = {}", tableId.toString());
        // Log Selected Columns for Debugging
        System.out.println("selected Columns: " + selectedColumns);

 
        // Define the schema based on selected columns
        Schema schema = Schema.of(selectedColumns.stream()
                .map(column -> {
                    System.out.println("Adding Columns to schema:" + column);
                    return Field.of(column, StandardSQLTypeName.STRING);// Assume all columns are of type STRING
                })
                .collect(Collectors.toList()));
        // Log Schema
        System.out.println("Schema:" + schema);

 
        LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(tableId, gcsFilePath)
                .setSchema(schema)
                .setFormatOptions(FormatOptions.csv().toBuilder().setSkipLeadingRows(1).build())
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
                .setIgnoreUnknownValues(true)
                .setMaxBadRecords(5) // Allow up to max 5 records
                .build();

 
        try {
           
            String jobName = "jobId_" + UUID.randomUUID().toString();
            JobId jobId = JobId.newBuilder().setLocation("us").setJob(jobName).setProject(computeProjectId)
                    .build();
            logger.info("Compute Project ID : {}", computeProjectId);
            // log for submitting job with selected columns
            logger.info("Submitting Bigquery job for table creation with selected Columns: {}", tableName);

 
            Job job = bigQuery.create(JobInfo.of(jobId, loadConfig));
            job = job.waitFor();
            if (job.isDone()) {
                logger.info("Table created/replaced successfully with selected columns: {}.{}.{}", projectId,
                        datasetName, tableName);
            } else {
                throw new RuntimeException("BigQuery create table job failed: " + job.getStatus().getError());
            }
        }
        catch (InterruptedException e) {
            throw new RuntimeException("BigQuery job was interrupted", e);
        }
    }

 
    @Override
    public List<String> getColumnsFromFile(MultipartFile file) {
        logger.info("Extracting columns from file...");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = br.readLine();
            if (headerLine != null) {
                System.out.println("Extracted header line: " + headerLine);
                return Arrays.stream(headerLine.split(","))
                    .map(String::trim) // Trim each column name
                    .map(column -> column.replaceAll("[^\\p{Print}]",""))
                    .collect(Collectors.toList());
            }
        }
        catch (IOException e) {
            logger.error("Failed to extract columns from file:{}", e.getMessage(), e);
            throw new StorageException("Failed to extract columns from file", e);
        }
        return Collections.emptyList();
    }

 
    /**
     * Initializes the Storage Service.
     * this is placeholder to indicate initialization.
     */
    @Override
    public void init() {
        logger.info("GCS Storage bucket initializad.");
    }

 
    /**
     * Unsporrted operation: Loading a file from GCS is not implemented.
     *
     * @param filename the name of file of load.
     * @throws UnsupportedOperationException when invoked.
     */
    @Override
    public Path load(String filename) {
        throw new UnsupportedOperationException("load operation is not supported for gcs.");
    }

 
    /**
     * Unsporrted operation: Delete all file.
     * currently only logs a message indicating deletion is unsupported.
     */
    @Override
    public void deleteAll() {
    }

 
    /**
     * Loads all files in a temporary directory.
     *
     * @throws StorageException if reading files fails.
     */
    @Override
    public Stream<Path> loadAll() {
        try {
            // Use the systems's temp directory
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            return Files.walk(tempDir, 1).filter(path -> !path.equals(tempDir))
                    .map(Path::normalize);
        } catch (IOException e) {
            throw new StorageException("Failed to read stored files", e);
        }

 
    }

 
    /**
     * Unsporrted operation: Loading a file as a resource.
     *
     * @param filename the name of file of load.
     * @throws UnsupportedOperationException when invoked.
     */
    @Override
    public Resource loadAsResource(String filename) {
        throw new UnsupportedOperationException("LoadasResource operation is not supported.");
    }
}
