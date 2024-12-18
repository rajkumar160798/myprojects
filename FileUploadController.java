package com.cvs.anbc.ahreports.controller;

import java.io.IOException;
import java.util.stream.Collectors;

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
        try {
            storageService.store(file);
            uploadedFileName = file.getOriginalFilename();
            redirectAttributes.addFlashAttribute("message",
                    "You successfully uploaded " + file.getOriginalFilename() + "!");
        } catch (Exception e) {
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
            if (uploadedFileName == null) {
                redirectAttributes.addFlashAttribute("message", "No file uploaded yet!");
                return "redirect:/";
            }
            storageService.createOrReplaceBigQueryTable(uploadedFileName, datasetName, tableName);
            redirectAttributes.addFlashAttribute("message",
                    "Table created/replaced successfully in BigQuery: " + datasetName + "." + tableName);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message",
                    "Failed to create table: " + e.getMessage());
        }
        return "redirect:/";
    }

    // Exception Handler
    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
}
