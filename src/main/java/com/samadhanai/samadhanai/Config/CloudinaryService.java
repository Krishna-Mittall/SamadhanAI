package com.samadhanai.samadhanai.Config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Cloudinary Service for photo storage
 * Provides persistent cloud storage for complaint photos
 */
@Service
@Slf4j
public class CloudinaryService {

    @Value("${cloudinary.cloud_name:}")
    private String cloudName;

    @Value("${cloudinary.api_key:}")
    private String apiKey;

    @Value("${cloudinary.api_secret:}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        if (cloudName.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
            log.warn("⚠️ Cloudinary credentials not configured. Photo uploads will fail.");
            return;
        }
        
        cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret,
            "secure", true
        ));
        
        log.info("✅ Cloudinary initialized for cloud: {}", cloudName);
    }

    /**
     * Upload photo to Cloudinary
     * @param file MultipartFile to upload
     * @return Full URL of uploaded image
     * @throws IOException if upload fails
     */
    public String uploadPhoto(MultipartFile file) throws IOException {
        if (cloudinary == null) {
            throw new IOException("Cloudinary not configured. Check environment variables.");
        }

        try {
            log.info("📤 Uploading photo to Cloudinary: {}", file.getOriginalFilename());
            
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", "samadhanai/complaints",
                    "resource_type", "image",
                    "use_filename", false,
                    "unique_filename", true
                )
            );

            String url = uploadResult.get("secure_url").toString();
            log.info("✅ Photo uploaded successfully: {}", url);
            
            return url;
        } catch (Exception e) {
            log.error("❌ Cloudinary upload failed: {}", e.getMessage());
            throw new IOException("Failed to upload photo to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Cloudinary is configured
     * @return true if credentials are set
     */
    public boolean isConfigured() {
        return cloudinary != null;
    }
}
