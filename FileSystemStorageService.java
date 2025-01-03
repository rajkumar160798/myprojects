package com.cvs.anbc.ahreports.storage;

import com.cvs.anbc.ahreports.dao.StorageProperties;
import com.cvs.anbc.ahreports.dao.BigQueryProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.google.cloud.bigquery.*;
import com.google.cloud.storage.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Service
public class FileSystemStorageService implements StorageService {

    private final Storage storage;
    private final BigQuery bigQuery;
    private final StorageProperties storageProperties;
    private final BigQueryProperties bigQueryProperties;

    private String bucketName;
    private String projectId;

    public FileSystemStorageService(StorageProperties storageProperties, BigQueryProperties bigQueryProperties) {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bigQuery = BigQueryOptions.getDefaultInstance().getService();
        this.storageProperties = storageProperties;
        this.bigQueryProperties = bigQueryProperties;

        // Set default environment
        this.bucketName = storageProperties.getBucketName();
        this.projectId = bigQueryProperties.getProjectId();
    }

    public void setEnvironment(String environment) {
        switch (environment.toLowerCase()) {
            case "dev":
                this.bucketName = storageProperties.getDevBucketName();
                this.projectId = bigQueryProperties.getDevProjectId();
                break;
            case "test":
                this.bucketName = storageProperties.getTestBucketName();
                this.projectId = bigQueryProperties.getTestProjectId();
                break;
            case "prod":
                this.bucketName = storageProperties.getProdBucketName();
                this.projectId = bigQueryProperties.getProdProjectId();
                break;
            default:
                throw new IllegalArgumentException("Invalid environment: " + environment);
        }
        System.out.println("Environment set to: " + environment.toUpperCase());
        System.out.println("Bucket Name: " + this.bucketName);
        System.out.println("Project ID: " + this.projectId);
    }

    @Override
    public void store(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }
            Path tempDir = Files.createTempDirectory("upload-temp");
            Path tempFilePath = tempDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFilePath, StandardCopyOption.REPLACE_EXISTING);

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, file.getOriginalFilename()).build();
            storage.create(blobInfo, Files.readAllBytes(tempFilePath));

            Files.deleteIfExists(tempFilePath);
            Files.deleteIfExists(tempDir);
        } catch (IOException e) {
            throw new StorageException("Failed to upload file to GCS bucket.", e);
        }
    }

    @Override
    public void createOrReplaceBigQueryTable(String fileName, String datasetName, String tableName) {
        String gcsFilePath = "gs://" + bucketName + "/" + fileName;
        TableId tableId = TableId.of(projectId, datasetName, tableName);

        LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(tableId, gcsFilePath)
                .setFormatOptions(FormatOptions.csv())
                .setAutodetect(true)
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
                .build();

        try {
            Job job = bigQuery.create(JobInfo.of(loadConfig));
            job = job.waitFor();
            if (job.isDone()) {
                System.out.println("Table created successfully: " + tableId);
            } else {
                throw new RuntimeException("BigQuery table creation failed: " + job.getStatus().getError());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("BigQuery job was interrupted.", e);
        }
    }
}
