package com.cvs.anbc.ahreports.controller;


 

import java.io.BufferedReader;

import java.io.InputStreamReader;

import java.util.ArrayList;

import java.util.List;

import java.util.stream.Collectors;


 

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

            // Store the file

            storageService.store(file);

            uploadedFileName = file.getOriginalFilename();


 

            // Extract columns (if CSV)

            if (file.getOriginalFilename().endsWith(".csv")) {

                try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

                    String headerLine = br.readLine();

                    if (headerLine != null) {

                        columnHeaders = List.of(headerLine.split(","));

                    }

                }

            } else {

                columnHeaders = new ArrayList<>(); // Clear if not CSV

            }


 

            redirectAttributes.addFlashAttribute("message",

                    "You successfully uploaded " + file.getOriginalFilename() + "!");

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute("message",

                    "Failed to upload file: " + e.getMessage());

        }

        return "redirect:/";

    }


 

    @PostMapping("/create-table")

    public String createBigQueryTable(@RequestParam("dataset") String datasetName,

                                      @RequestParam("table") String tableName,

                                      @RequestParam("columns") List<String> selectedColumns,

                                      RedirectAttributes redirectAttributes) {

        try {

            if (uploadedFileName == null) {

                redirectAttributes.addFlashAttribute("message", "No file uploaded yet!");

                return "redirect:/";

            }


 

            // Pass selected columns to the storage service

            storageService.createOrReplaceBigQueryTableWithColumns(uploadedFileName, datasetName, tableName, selectedColumns);


 

            redirectAttributes.addFlashAttribute("message",

                    "Table created successfully in BigQuery with selected columns: " + String.join(", ", selectedColumns));

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute("message",

                    "Failed to create table: " + e.getMessage());

        }

        return "redirect:/";

    }

}