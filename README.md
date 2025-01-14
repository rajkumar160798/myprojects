# Ah Reports Web Utility App 

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Setup Instructions](#setup-instructions)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Configuration](#configuration)
  - [Running the Application](#running-the-application)
- [Environment Links](#environment-links)
- [API Endpoints](#api-endpoints)
  - [Upload File](#upload-file)
  - [Create BigQuery Table](#create-bigquery-table)
- [Logging and Monitoring](#logging-and-monitoring)
  - [Log Levels](#log-levels)
  - [Log Access](#log-access)
  - [Health Monitoring](#health-monitoring)
- [Contributing](#contributing)
- [Contact](#contact)

## Overview
The Ah Reports Web Utility App is designed to streamline data upload, processing, and integration with Google Cloud services like BigQuery and GCS. It supports multiple environments and provides robust error handling and logging.

## Features
- Upload files directly to GCS.
- Create or replace BigQuery tables with uploaded data.
- Extract and validate data columns.
- Environment-specific configurations.
- Detailed logging for monitoring and debugging.

## Setup Instructions

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Kubernetes cluster (for deployment)
- Google Cloud Project with the necessary IAM permissions:
  - `roles/storage.objectAdmin`
  - `roles/bigquery.jobUser`

### Installation
1. Clone the repository:
    ```sh
    git clone https://github.com/your-repo/ah-reports-web-utility.git
    cd ah-reports-web-utility
    ```

2. Build the project using Maven:
    ```sh
    mvn clean install
    ```

### Configuration
1. Update the `application.yaml` file with your specific configurations:
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
      bucketName: ah-reports-code
    ```

2. Set up your Google Cloud credentials:
    ```sh
    export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/credentials.json"
    ```

### Running the Application
1. Run the application locally:
    ```sh
    mvn spring-boot:run
    ```

2. Access the application at `http://localhost:8080`.

## Environment Links
Access the application in different environments:

- Development: http://dev.your-app.com
- Testing: http://test.your-app.com
- Production: http://prod.your-app.com

## API Endpoints

### Upload File
- **Endpoint**: `/upload`
- **Method**: `POST`
- **Description**: Upload a file to a specified GCS folder.

**Example Request**:

```bash
curl -X POST http://<env-url>/upload \
     -F "file=@example.csv" 
```

### Create BigQuery Table
- **Endpoint**: `/create-table`
- **Method**: `POST`
- **Description**: Create or replace a BigQuery table with the uploaded data.

**Example Request**:

```bash
curl -X POST http://<env-url>/create-table \
     -d '{"fileName": "example.csv", "datasetName": "test_dataset", "tableName": "test_table"}' \
     -H "Content-Type: application/json"
```

## Logging and Monitoring

### Log Levels
Configure log levels in `application.yaml`:

```yaml
logging:
  level:
    root: INFO
    com.cvs.anbc.ahreports: DEBUG
```

### Log Access
- Local logs: `logs/web-utility-app.log`
- Kubernetes logs:

```bash
kubectl logs <pod-name>
```

### Health Monitoring
- **Health Check**: `/actuator/health`
- **Metrics**: `/actuator/metrics`

## Contributing
We welcome contributions! Please follow these steps to contribute:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature-branch`).
3. Make your changes.
4. Commit your changes (`git commit -am 'Add new feature'`).
5. Push to the branch (`git push origin feature-branch`).
6. Create a new Pull Request.

## Contact
For any questions or support, reach out to:

- Development Team: dev-team@yourcompany.com
- Slack: `#utility-app-support`