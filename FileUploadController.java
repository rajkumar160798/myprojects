package com.cvs.anbc.ahreports.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.cvs.anbc.ahreports.storage.StorageService;

@Controller
public class FileUploadController {

    private final StorageService storageService;
    private String uploadedFileName;
    private List<String> columnHeaders = new ArrayList<>();

    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/")
    public String listUploadedFiles(Model model) {
        model.addAttribute("columns", columnHeaders);
        return "uploadForm";
    }

    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("message", "Please select a file to upload.");
                return "redirect:/";
            }

            // Store the file
            storageService.store(file);
            uploadedFileName = file.getOriginalFilename();

            // Extract columns (if CSV)
            if (uploadedFileName.endsWith(".csv")) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                    String headerLine = br.readLine();
                    if (headerLine != null) {
                        columnHeaders = List.of(headerLine.split(","));
                    }
                }
            } else {
                columnHeaders.clear(); // Clear if not CSV
            }

            redirectAttributes.addFlashAttribute("message", "Successfully uploaded: " + uploadedFileName);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Failed to upload file: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/create-table")
    public String createBigQueryTable(@RequestParam("dataset") String datasetName,
                                      @RequestParam("table") String tableName,
                                      @RequestParam(value = "columns", required = false) List<String> selectedColumns,
                                      RedirectAttributes redirectAttributes) {
        try {
            if (uploadedFileName == null) {
                redirectAttributes.addFlashAttribute("message", "No file uploaded yet!");
                return "redirect:/";
            }

            // Create table in BigQuery
            storageService.createOrReplaceBigQueryTableWithColumns(uploadedFileName, datasetName, tableName, selectedColumns);

            redirectAttributes.addFlashAttribute("message", "Table created successfully in BigQuery.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Failed to create table: " + e.getMessage());
        }
        return "redirect:/";
    }
}
