import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import com.google.cloud.bigquery.*;

public void createOrReplaceBigQueryTableWithInferredTypes(String fileName, String datasetName, String tableName) {
    String gcsFilePath = "gs://" + bucketName + "/" + fileName;
    logger.info("GCS File Path: {}", gcsFilePath);
    logger.info("Project ID = {}", projectId);

    TableId tableId = TableId.of(projectId, datasetName, tableName);
    logger.info("Table ID: = {}", tableId.toString());

    List<String> headerColumns = new ArrayList<>();
    Map<String, StandardSQLTypeName> inferredColumnTypes = new HashMap<>();

    try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(gcsFilePath)), StandardCharsets.UTF_8))) {
        // Read the header to get the column names
        String headerLine = br.readLine();
        if (headerLine != null) {
            headerColumns = Arrays.asList(headerLine.split(","));
            logger.info("Detected columns: {}", headerColumns);
        } else {
            throw new RuntimeException("File is empty or doesn't contain a header.");
        }

        // Sample data (for example, the first 100 rows) to infer types
        List<List<String>> rows = new ArrayList<>();
        String line;
        int rowCount = 0;
        while ((line = br.readLine()) != null && rowCount < 100) {
            rows.add(Arrays.asList(line.split(",")));
            rowCount++;
        }

        // Infer data types for each column
        for (int colIndex = 0; colIndex < headerColumns.size(); colIndex++) {
            String columnName = headerColumns.get(colIndex);
            List<String> columnData = rows.stream()
                    .map(row -> row.get(colIndex))
                    .collect(Collectors.toList());
            inferredColumnTypes.put(columnName, inferColumnType(columnData));
        }

    } catch (IOException e) {
        logger.error("Error reading file header: {}", e.getMessage(), e);
        throw new RuntimeException("Error reading file header", e);
    }

    // Generate schema based on inferred column types
    Schema schema = Schema.of(inferredColumnTypes.entrySet().stream()
            .map(entry -> Field.of(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList()));

    logger.info("Inferred Schema: {}", schema);

    // Proceed with the BigQuery upload process
    LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(tableId, gcsFilePath)
            .setSchema(schema)
            .setFormatOptions(FormatOptions.csv().toBuilder().setSkipLeadingRows(1).build())
            .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
            .setIgnoreUnknownValues(true)
            .setMaxBadRecords(5) // Allow up to 5 bad records
            .build();

    try {
        String jobName = "jobId_" + UUID.randomUUID().toString();
        JobId jobId = JobId.newBuilder().setLocation("us").setJob(jobName).setProject(computeProjectId)
                .build();
        logger.info("Compute Project ID : {}", computeProjectId);

        Job job = bigQuery.create(JobInfo.of(jobId, loadConfig));
        job = job.waitFor();

        if (job.isDone()) {
            logger.info("Table created/replaced successfully with inferred schema: {}.{}.{}", projectId,
                    datasetName, tableName);
        } else {
            throw new RuntimeException("BigQuery create table job failed: " + job.getStatus().getError());
        }
    } catch (InterruptedException e) {
        throw new RuntimeException("BigQuery job was interrupted", e);
    }
}

// Method to infer the type of data based on column values
private StandardSQLTypeName inferColumnType(List<String> columnData) {
    for (String value : columnData) {
        // Check if the value can be parsed as a date
        if (isDate(value)) {
            return StandardSQLTypeName.DATE;
        }
        // Check if the value can be parsed as an integer
        if (isInteger(value)) {
            return StandardSQLTypeName.INTEGER;
        }
        // Check if the value can be parsed as a float
        if (isFloat(value)) {
            return StandardSQLTypeName.FLOAT64;
        }
    }
    // If no valid type is found, return STRING
    return StandardSQLTypeName.STRING;
}

// Helper methods to check data types
private boolean isDate(String value) {
    try {
        // Try parsing as a date (example: "yyyy-MM-dd")
        java.time.LocalDate.parse(value);
        return true;
    } catch (Exception e) {
        return false;
    }
}

private boolean isInteger(String value) {
    try {
        Integer.parseInt(value);
        return true;
    } catch (NumberFormatException e) {
        return false;
    }
}

private boolean isFloat(String value) {
    try {
        Float.parseFloat(value);
        return true;
    } catch (NumberFormatException e) {
        return false;
    }
}
