@Override
public void createOrReplaceBigQueryTableWithColumns(String fileName, String datasetName, String tableName,
        List<String> selectedColumns) {
    String gcsFilePath = "gs://" + bucketName + "/" + fileName;
    logger.info("GCS File Path: {}", gcsFilePath);
    logger.info("Project ID = {}", projectId);

    TableId tableId = TableId.of(projectId, datasetName, tableName);
    logger.info("Table ID: = {}", tableId.toString());

    // Read the header of the CSV to dynamically detect columns
    List<String> headerColumns = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(gcsFilePath)), StandardCharsets.UTF_8))) {
        String headerLine = br.readLine();
        if (headerLine != null) {
            headerColumns = Arrays.asList(headerLine.split(","));
            logger.info("Detected columns: {}", headerColumns);
        } else {
            throw new RuntimeException("File is empty or doesn't contain a header.");
        }
    } catch (IOException e) {
        logger.error("Error reading file header: {}", e.getMessage(), e);
        throw new RuntimeException("Error reading file header", e);
    }

    // Validate if selected columns exist in the header
    if (!headerColumns.containsAll(selectedColumns)) {
        throw new RuntimeException("Selected columns are not present in the file header.");
    }

    // Dynamically map columns to their data types
    Schema schema = Schema.of(selectedColumns.stream()
            .map(column -> {
                // Dynamically decide column type based on its name
                if (column.contains("dt") || column.toLowerCase().endsWith("_dt")) {
                    return Field.of(column, StandardSQLTypeName.DATE);
                } else if (column.contains("id") || column.toLowerCase().endsWith("_id")) {
                    return Field.of(column, StandardSQLTypeName.STRING); // Treat IDs as strings (alphanumeric)
                } else {
                    return Field.of(column, StandardSQLTypeName.STRING);  // Default to STRING
                }
            })
            .collect(Collectors.toList()));

    logger.info("Schema: {}", schema);

    // Reorder the columns based on selectedColumns
    try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(gcsFilePath)), StandardCharsets.UTF_8))) {
        String headerLine = br.readLine(); // Skip header
        String line;
        List<String> reorderedData = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            List<String> values = Arrays.asList(line.split(","));
            List<String> reorderedValues = selectedColumns.stream()
                .map(column -> values.get(headerColumns.indexOf(column)))  // Get values in the selected order
                .collect(Collectors.toList());

            reorderedData.add(String.join(",", reorderedValues));  // Rejoin the reordered values
        }

        // Now, you can upload the reordered data to BigQuery
        // Create a temporary file with reordered data
        Path tempFile = Files.createTempFile("reordered_", ".csv");
        Files.write(tempFile, reorderedData);
        gcsFilePath = "gs://" + bucketName + "/" + tempFile.getFileName();

        // Proceed with the BigQuery upload process
        LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(tableId, gcsFilePath)
                .setSchema(schema)
                .setFormatOptions(FormatOptions.csv().toBuilder().setSkipLeadingRows(1).build())
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
                .setIgnoreUnknownValues(true)
                .setMaxBadRecords(5) // Allow up to 5 bad records
                .build();

        String jobName = "jobId_" + UUID.randomUUID().toString();
        JobId jobId = JobId.newBuilder().setLocation("us").setJob(jobName).setProject(computeProjectId)
                .build();
        logger.info("Compute Project ID : {}", computeProjectId);
        // Log for submitting job with selected columns
        logger.info("Submitting BigQuery job for table creation with selected Columns: {}", tableName);

        Job job = bigQuery.create(JobInfo.of(jobId, loadConfig));
        job = job.waitFor();

        if (job.isDone()) {
            logger.info("Table created/replaced successfully with selected columns: {}.{}.{}", projectId,
                    datasetName, tableName);
        } else {
            throw new RuntimeException("BigQuery create table job failed: " + job.getStatus().getError());
        }
    } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Error processing file for BigQuery load", e);
    }
}
