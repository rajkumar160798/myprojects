package com.cvs.anbc.ahreports.controller;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.cvs.anbc.ahreports.exceptions.StorageFileNotFoundException;
import com.cvs.anbc.ahreports.storage.StorageService;

@Controller
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);
    private final StorageService storageService;
    private String uploadedFileName;

    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    // Displays uploaded files list
    @GetMapping("/")
    public String listUploadedFiles(Model model) throws IOException {
        model.addAttribute("files", storageService.loadAll().map(
                path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class,
                        "serveFile", path.getFileName().toString()).build().toUri().toString())
                .collect(Collectors.toList()));

        return "uploadForm";
    }

    // Serves uploaded files
    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource file = storageService.loadAsResource(filename);
        if (file == null)
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }

    // Handles file upload
    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {
        logger.info("Received file upload request. File name: {}, Size: {} bytes",
                file.getOriginalFilename(), file.getSize());
        try {
            storageService.store(file);
            uploadedFileName = file.getOriginalFilename();
            List<String> columnNames = storageService.getColumnsFromFile(file);
            logger.info("File: {} uploaded successfully.", file.getOriginalFilename());
            redirectAttributes.addFlashAttribute("columns", columnNames);
            redirectAttributes.addFlashAttribute("message",
                    "You successfully uploaded " + file.getOriginalFilename() + "!");
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Failed to upload file: {}", file.getOriginalFilename(), e.getMessage());
            redirectAttributes.addFlashAttribute("message",
                    "Failed to upload file: " + e.getMessage());
        }
        return "redirect:/";
    }

    // BigQuery Table Creation Handler
    @PostMapping("/create-table")
    public String createBigQueryTable(@RequestParam("dataset") String datasetName,
            @RequestParam("table") String tableName,
            RedirectAttributes redirectAttributes) {
        try {
            storageService.createOrReplaceBigQueryTable(uploadedFileName, datasetName, tableName);
            redirectAttributes.addFlashAttribute("message", "Table created with all columns");
            redirectAttributes.addFlashAttribute("message", "Table created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message", "Failed to create table: " + e.getMessage());
        }
        return "redirect:/";
    }

    // Exception Handler
    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
}