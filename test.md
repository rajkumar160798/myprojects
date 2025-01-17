# File Upload and BigQuery Table Creation - Project Overview

This project provides functionality to upload a file, extract column names, and create a BigQuery table with the option to select specific columns. Below is a breakdown of the code and its responsibilities.

---

### 1. **Controller Class: `FileUploadController.java`**

The `FileUploadController` class is responsible for handling the HTTP requests related to file uploads and BigQuery table creation.

#### Methods:
- **`listUploadedFiles` (GET "/"):**
  - Displays the list of uploaded files and allows the user to select files for further processing. The uploaded files are retrieved using the `storageService.loadAll()` method.
  - The `MvcUriComponentsBuilder` is used to generate a URI for serving each uploaded file.
  - The list of files is sent to the `uploadForm` view.

- **`serveFile` (GET "/files/{filename}"):**
  - Serves a specific uploaded file by its filename. The file is retrieved using the `storageService.loadAsResource(filename)` method and returned as a downloadable resource.
  - This method supports the `ResponseEntity` class to manage file download responses.

- **`handleFileUpload` (POST "/"):**
  - Handles the file upload from the frontend form. The uploaded file is processed and stored using the `storageService.store(file)` method.
  - After storing the file, it extracts the columns from the file using `storageService.getColumnsFromFile(file)` and stores them in the model as `columns`.
  - A success message is returned with the uploaded file's name.

- **`createBigQueryTable` (POST "/create-table"):**
  - Creates a BigQuery table from the uploaded CSV file. The user can choose to create the table with all columns or select specific columns.
  - If columns are selected, the `storageService.createOrReplaceBigQueryTableWithColumns()` method is used to create the table with the selected columns. If not, the full table is created using `storageService.createOrReplaceBigQueryTable()`.
  - After the table is created, a success message with the table name is shown on the frontend.

#### Exception Handling:
- **`handleStorageFileNotFound`**:
  - Handles exceptions when files are not found, returning a `404 Not Found` response.

---

### 2. **Service Class: `StorageService.java`**

The `StorageService` interface defines the methods for storing files and interacting with Google Cloud Storage (GCS) and BigQuery. The actual implementation is provided by the `FileSystemStorageService` class.

#### Key Methods:
- **`store`**: 
  - Saves the uploaded file to the local file system and then uploads it to Google Cloud Storage (GCS).
  - It creates a temporary directory, writes the file to it, and then uploads it to GCS using the `Storage` client.
  
- **`getColumnsFromFile`**: 
  - Reads the first line of the CSV file to extract the column names.
  - This is done by reading the file and splitting the first row based on commas.

- **`createOrReplaceBigQueryTable`**: 
  - Creates or replaces a table in BigQuery using the uploaded file and a specified dataset and table name.
  - If the table already exists, it is replaced with the new data.

- **`createOrReplaceBigQueryTableWithColumns`**: 
  - Similar to `createOrReplaceBigQueryTable`, but only selected columns from the file are used to create the table.
  - This method is used when the user selects specific columns to be included in the BigQuery table.

---

### 3. **Frontend: `index.html`**

The frontend HTML form is used for file upload and BigQuery table creation.

#### Key Features:
- **File Upload Form**:
  - Allows the user to upload a CSV file, enter a dataset name, and specify a table name.
  - The form sends a POST request to the backend to upload the file and process it.

- **Table Creation Form**:
  - Allows the user to specify a dataset and table name for BigQuery table creation.
  - Users can select specific columns or create a table with all columns.
  - The form sends a POST request to create the table with the specified columns.

- **Success/Error Messages**:
  - Success or error messages are displayed based on the actions performed (e.g., file uploaded successfully, table created, etc.).
  - Flash attributes (`message`) are used to pass messages from the backend to the frontend.

---

### 4. **Error Handling**

The project includes basic error handling to catch exceptions during file upload and table creation.

- **File Upload Failures**:
  - If an error occurs while uploading the file, the error message is passed to the frontend to be displayed to the user.

- **BigQuery Table Creation Failures**:
  - If an error occurs while creating the BigQuery table, the exception is caught and an error message is passed to the frontend.

---

### 5. **Google Cloud Integration**

The project integrates with Google Cloud Storage and BigQuery for file storage and data analysis.

- **Google Cloud Storage (GCS)**:
  - Files are uploaded to a specified GCS bucket and can be accessed via URLs.
  
- **BigQuery**:
  - After a file is uploaded, it can be used to create a new BigQuery table or update an existing one.
  - The table schema is either auto-detected or defined based on the selected columns.

---

### 6. **Usage Instructions**

To run this project locally, follow these steps:

1. **Clone the repository:**
    ```sh
    git clone <repository-url>
    cd <repository-directory>
    ```

2. **Set up Google Cloud credentials:**
    - Ensure you have a Google Cloud account and the necessary permissions for Google Cloud Storage and BigQuery.
    - Set up your Google Cloud credentials by following the [Google Cloud Authentication Guide](https://cloud.google.com/docs/authentication/getting-started).

3. **Configure application properties:**
    - Update the [application.yaml](http://_vscodecontentref_/1) file with your specific Google Cloud Storage bucket name and other configurations.
    - Example:
        ```yaml
        env: default

        spring:
          profile: local
          application:
            name: ah-reports-utils
          servlet:
            multipart:
              max-file-size: 128kb
              max-request-size: 128kb

        storage:
          bucketName: your-bucket-name
        ```

4. **Build and run the application:**
    ```sh
    ./mvnw spring-boot:run
    ```

5. **Access the application:**
    - Open your web browser and navigate to [http://localhost:8080](http://_vscodecontentref_/2).
    - You should see the file upload form.

6. **Upload a file and create a BigQuery table:**
    - Select an environment, upload a CSV file, and follow the steps to create a BigQuery table.

---

### 7. **Additional Resources**

- **Spring Boot Documentation:** [https://spring.io/projects/spring-boot](https://spring.io/projects/spring-boot)
- **Google Cloud Storage Documentation:** [https://cloud.google.com/storage/docs](https://cloud.google.com/storage/docs)
- **Google BigQuery Documentation:** [https://cloud.google.com/bigquery/docs](https://cloud.google.com/bigquery/docs)

---

### 8. **Contributing**

If you would like to contribute to this project, please follow these steps:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature-branch`).
3. Make your changes and commit them (`git commit -m 'Add new feature'`).
4. Push to the branch (`git push origin feature-branch`).
5. Create a new Pull Request.

---

### 9. **License**

This project is licensed under the MIT License. See the LICENSE file for more details.
---

### 1. **Controller Class: `FileUploadController.java`**

The `FileUploadController` class is responsible for handling the HTTP requests related to file uploads and BigQuery table creation.

#### Methods:
- **`listUploadedFiles` (GET "/"):**
  - Displays the list of uploaded files and allows the user to select files for further processing. The uploaded files are retrieved using the `storageService.loadAll()` method.
  - The `MvcUriComponentsBuilder` is used to generate a URI for serving each uploaded file.
  - The list of files is sent to the `uploadForm` view.

- **`serveFile` (GET "/files/{filename}"):**
  - Serves a specific uploaded file by its filename. The file is retrieved using the `storageService.loadAsResource(filename)` method and returned as a downloadable resource.
  - This method supports the `ResponseEntity` class to manage file download responses.

- **`handleFileUpload` (POST "/"):**
  - Handles the file upload from the frontend form. The uploaded file is processed and stored using the `storageService.store(file)` method.
  - After storing the file, it extracts the columns from the file using `storageService.getColumnsFromFile(file)` and stores them in the model as `columns`.
  - A success message is returned with the uploaded file's name.

- **`createBigQueryTable` (POST "/create-table"):**
  - Creates a BigQuery table from the uploaded CSV file. The user can choose to create the table with all columns or select specific columns.
  - If columns are selected, the `storageService.createOrReplaceBigQueryTableWithColumns()` method is used to create the table with the selected columns. If not, the full table is created using `storageService.createOrReplaceBigQueryTable()`.
  - After the table is created, a success message with the table name is shown on the frontend.

#### Exception Handling:
- **`handleStorageFileNotFound`**:
  - Handles exceptions when files are not found, returning a `404 Not Found` response.

---

### 2. **Service Class: `StorageService.java`**

The `StorageService` interface defines the methods for storing files and interacting with Google Cloud Storage (GCS) and BigQuery. The actual implementation is provided by the `FileSystemStorageService` class.

#### Key Methods:
- **`store`**: 
  - Saves the uploaded file to the local file system and then uploads it to Google Cloud Storage (GCS).
  - It creates a temporary directory, writes the file to it, and then uploads it to GCS using the `Storage` client.
  
- **`getColumnsFromFile`**: 
  - Reads the first line of the CSV file to extract the column names.
  - This is done by reading the file and splitting the first row based on commas.

- **`createOrReplaceBigQueryTable`**: 
  - Creates or replaces a table in BigQuery using the uploaded file and a specified dataset and table name.
  - If the table already exists, it is replaced with the new data.

- **`createOrReplaceBigQueryTableWithColumns`**: 
  - Similar to `createOrReplaceBigQueryTable`, but only selected columns from the file are used to create the table.
  - This method is used when the user selects specific columns to be included in the BigQuery table.

---
