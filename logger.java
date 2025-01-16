@Override
public void createOrReplaceBigQueryTableWithColumns(String fileName, String datasetName, String tableName,
        List<String> selectedColumns) {
    String gcsFilePath = "gs://" + bucketName + "/" + fileName;
    logger.info("GCS File Path: {}", gcsFilePath);
    logger.info("Project ID = {}", projectId);

    TableId tableId = TableId.of(projectId, datasetName, tableName);
    logger.info("Table ID: = {}", tableId.toString());

    // Read the file and extract the header to get the actual column names
    List<String> headerColumns = new ArrayList<>();
    List<List<String>> rows = new ArrayList<>();
    
    try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(gcsFilePath)), StandardCharsets.UTF_8))) {
        // Read the header to get the column names
        String headerLine = br.readLine();
        if (headerLine != null) {
            headerColumns = Arrays.asList(headerLine.split(","));
            logger.info("Detected columns: {}", headerColumns);
        } else {
            throw new RuntimeException("File is empty or doesn't contain a header.");
        }

        // Validate if selected columns exist in the header
        if (!headerColumns.containsAll(selectedColumns)) {
            throw new RuntimeException("Selected columns are not present in the file header.");
        }

        // Sample data (for example, the first 100 rows) to infer types
        String line;
        int rowCount = 0;
        while ((line = br.readLine()) != null && rowCount < 100) {
            rows.add(Arrays.asList(line.split(",")));
            rowCount++;
        }
    } catch (IOException e) {
        logger.error("Error reading file header: {}", e.getMessage(), e);
        throw new RuntimeException("Error reading file header", e);
    }

    // Reorder the columns according to the selectedColumns order
    List<String> reorderedColumns = new ArrayList<>();
    for (String column : selectedColumns) {
        if (headerColumns.contains(column)) {
            reorderedColumns.add(column);
        }
    }

    // Map the schema to the selected columns (assuming all columns are STRING for simplicity)
    Schema schema = Schema.of(reorderedColumns.stream()
            .map(column -> Field.of(column, StandardSQLTypeName.STRING)) // Assuming STRING for all columns
            .collect(Collectors.toList()));

    logger.info("Schema: {}", schema);

    // Process the rows and reorder the data according to selectedColumns
    List<String> reorderedData = new ArrayList<>();
    for (List<String> row : rows) {
        List<String> reorderedRow = reorderedColumns.stream()
                .map(column -> row.get(headerColumns.indexOf(column))) // Ensure data matches selected column order
                .collect(Collectors.toList());
        reorderedData.add(String.join(",", reorderedRow));
    }

    // Write the reordered data to a temp file and upload it to GCS
    Path tempFile;
    try {
        tempFile = Files.createTempFile("reordered_", ".csv");
        Files.write(tempFile, reorderedData);
    } catch (IOException e) {
        logger.error("Error writing to temp file: {}", e.getMessage(), e);
        throw new RuntimeException("Error writing to temp file", e);
    }

    // Now proceed to upload the reordered data to BigQuery
    LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(tableId, "gs://" + bucketName + "/" + tempFile.getFileName())
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
        logger.info("Submitting BigQuery job for table creation with selected columns: {}", tableName);

        Job job = bigQuery.create(JobInfo.of(jobId, loadConfig));
        job = job.waitFor();

        if (job.isDone()) {
            logger.info("Table created/replaced successfully with selected columns: {}.{}.{}", projectId,
                    datasetName, tableName);
        } else {
            throw new RuntimeException("BigQuery create table job failed: " + job.getStatus().getError());
        }
    } catch (InterruptedException e) {
        throw new RuntimeException("BigQuery job was interrupted", e);
    }
}