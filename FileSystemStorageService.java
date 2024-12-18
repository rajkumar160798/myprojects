import java.io.IOException;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.Paths;

import java.nio.file.StandardCopyOption;

import java.util.stream.Stream;


 

import org.springframework.core.io.Resource;

import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;


 

import com.cvs.anbc.ahreports.dao.StorageProperties;

import com.cvs.anbc.ahreports.exceptions.StorageException;

import com.google.cloud.bigquery.BigQuery;

import com.google.cloud.bigquery.BigQueryOptions;

import com.google.cloud.bigquery.FormatOptions;

import com.google.cloud.bigquery.LoadJobConfiguration;

import com.google.cloud.bigquery.TableId;

import com.google.cloud.bigquery.Job;

import com.google.cloud.bigquery.JobInfo;

import com.google.cloud.storage.BlobInfo;

import com.google.cloud.storage.Storage;

import com.google.cloud.storage.StorageOptions;


 

/**

 * Service class to handle file storage and upload operations to Google Cloud Storage(GCS bucket).

 */

@Service

public class FileSystemStorageService implements StorageService {


 

    private final Storage storage; // GCS instance

    private final String bucketName; //Name of the GCS bucket where files are uploaded

    private final BigQuery bigQuery;


 

    /**

     * Constructor initializes the GCS Client and Bucket where we upload the file.

     * @param properties Configuration object contains details of Gcs bucket.

     * @throws StorageException if bucket name is empty.

     */

    public FileSystemStorageService(StorageProperties properties){

        // Validates that the bucket name is not empty

        if (properties.getBucketName().trim().length() == 0){

            throw new StorageException("GCS bucket name cannot be empty.");

        }


 

        // Initializes the GCS storage

        this.storage = StorageOptions.getDefaultInstance().getService();

        this.bigQuery = BigQueryOptions.getDefaultInstance().getService();

        this.bucketName = properties.getBucketName();

    }


 

    /**

     * Stores the file in temp directory and then uploads it to the GCS bucket.

     * @param file the uploaded file recieved.

     * @throws StorageException if the file is empty or upload to the GCS bucket fails.

    } */

    @Override

    public void store(MultipartFile file){

        Path tempDir = null;  //tempDir for storing file

        Path tempFilePath = null;  //Path to the temporary file


 

        try {

            //Checks if the file is empty

            if (file.isEmpty()){

                throw new StorageException("failed to store empty file.");

            }


 

            //creates a temp directory to hold the file.

            tempDir = Files.createTempDirectory("upload-temp");

            // Resolves the path of temp file.

            tempFilePath = tempDir.resolve(file.getOriginalFilename());


 

            // copies the file's input stream to the temp file

            Files.copy(file.getInputStream(),tempFilePath,StandardCopyOption.REPLACE_EXISTING);

            System.out.println("File saved to temp directory:"+ tempFilePath);

           

            // Preapres the metadata for the GCS object

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName,file.getOriginalFilename()).build();


 

            //Upload the File to GCS

            storage.create(blobInfo, Files.readAllBytes(tempFilePath));

            System.out.println("File uploaded to GCS bucket:"+ bucketName+ file.getOriginalFilename());


 

        } catch (IOException e){

            //handles Exception during file operations or GCS upload.

            throw new StorageException("Failed to upload file to GCS bucket",e);


 

        } finally {

            // Cleans up temporary files and directories

            try {

                if (tempFilePath!=null && Files.exists(tempFilePath)){

                    Files.delete(tempFilePath);

                }

                if (tempDir!=null && Files.exists(tempDir)){

                    Files.delete(tempDir);

                }

                System.out.println("Temporary files cleaned up.");

            } catch (IOException e){

                System.out.println("failed to clean up temp files"+ e.getMessage());

            }

        }

    }

    @Override

    public void createOrReplaceBigQueryTable(String fileName, String datasetName , String tableName){

        String projectId = "anbc-hcb-dev";

        String gcsFilePath = "gs://" + bucketName + "/" + fileName;


 

        TableId tableId = TableId.of(projectId,datasetName, tableName);

        LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(tableId,gcsFilePath)

                .setFormatOptions(FormatOptions.csv())

                .setAutodetect(true)

                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)

                .build();

        try {

            Job job = bigQuery.create(JobInfo.of(loadConfig));

            job = job.waitFor();


 

            if(job.isDone()){

                System.out.println("Table created/replaced succesfully:" + projectId +"."+ datasetName + "." + tableName );

            } else {

                throw new RuntimeException ("Bigquery create table job failed:" + job.getStatus().getError());

                }

            } catch (InterruptedException e){

                throw new RuntimeException("BigQuery job was interrupted", e);

        }

    }


 

    /**

     * Initializes the Storage Service.

     * this is placeholder to indicate initialization.

    */

    @Override

    public void init(){

        System.out.println("GCS Storage bucket initializad.");

    }


 

    /**

     * Unsporrted operation: Loading a file from GCS is not implemented.

     * @param filename the name of file of load.

     * @throws UnsupportedOperationException when invoked.

     */

    @Override

    public Path load(String filename){

        throw new UnsupportedOperationException("load operation is not supported for gcs.");

    }


 

    /**

     * Unsporrted operation: Delete all file.

     * currently only logs a message indicating deletion is unsupported.

     */

    @Override

    public void deleteAll(){

        System.out.println("delete operation is not supported.");

    }


 

    /**

     * Loads all files in a temporary directory.

     * @throws StorageException if reading files fails.

     */

    @Override

    public Stream<Path> loadAll() {

        try {

            // Use the systems's temp directory

            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));

            return Files.walk(tempDir,1).filter(path-> !path.equals(tempDir))

            .map(Path::normalize);

        }

        catch (IOException e) {

            throw new StorageException("Failed to read stored files", e);

        }


 

    }


 

    /**

     * Unsporrted operation: Loading a file as a resource.

     * @param filename the name of file of load.

     * @throws UnsupportedOperationException when invoked.

     */

    @Override

    public Resource loadAsResource(String filename){

        throw new UnsupportedOperationException("LoadasResource operation is not supported.");

    }

}




 