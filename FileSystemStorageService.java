package com.cvs.anbc.ahreports.storage;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.cvs.anbc.ahreports.exceptions.StorageException;
import com.google.cloud.bigquery.*;
import com.google.cloud.storage.*;

@Service
public class FileSystemStorageService implements StorageService {

    private final Storage storage;
    private final BigQuery bigQuery;
    private final String bucketName;

    public FileSystemStorageService(StorageProperties properties) {
        if (properties.getBucketName().trim().isEmpty()) {
            throw new StorageException("GCS bucket name cannot be empty.");
        }
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bigQuery = BigQueryOptions.getDefaultInstance().getService();
        this.bucketName = properties.getBucketName();
    }

    @Override
    public void store(MultipartFile file) {
        try {
            if (file.isEmpty()) throw new StorageException("Failed to store empty file.");

            Path tempDir = Files.createTempDirectory("upload-temp");
            Path tempFilePath = tempDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFilePath, StandardCopyOption.REPLACE_EXISTING);

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, file.getOriginalFilename()).build();
            storage.create(blobInfo, Files.readAllBytes(tempFilePath));

            Files.deleteIfExists(tempFilePath);
            Files.deleteIfExists(tempDir);
        } catch (IOException e) {
            throw new StorageException("Failed to upload file to GCS bucket", e);
        }
    }

    @Override
    public void createOrReplaceBigQueryTableWithColumns(String fileName, String datasetName, String tableName, List<String> selectedColumns) {
        String gcsFilePath = "gs://" + bucketName + "/" + fileName;

        Schema schema = Schema.of(selectedColumns.stream()
            .map(column -> Field.of(column, StandardSQLTypeName.STRING))
            .collect(Collectors.toList()));

        LoadJobConfiguration config = LoadJobConfiguration.newBuilder(
                TableId.of("anbc-hcb-dev", datasetName, tableName),
                gcsFilePath)
            .setSchema(schema)
            .setFormatOptions(FormatOptions.csv())
            .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
            .build();

        try {
            Job job = bigQuery.create(JobInfo.of(config)).waitFor();
            if (!job.isDone()) throw new RuntimeException("BigQuery table creation failed: " + job.getStatus().getError());
        } catch (InterruptedException e) {
            throw new RuntimeException("BigQuery job was interrupted", e);
        }
    }
}
