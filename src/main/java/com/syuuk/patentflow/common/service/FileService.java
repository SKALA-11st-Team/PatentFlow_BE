package com.syuuk.patentflow.common.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class FileService {

    private final S3Client s3Client;
    
    @Value("${s3.bucket.name}") // 버킷 이름도 .env 나 yml에서 관리하는 것이 좋습니다.
    private String bucketName;

    public FileService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    // 파일 업로드 메서드
    public String uploadFile(MultipartFile file) throws IOException {
        // 1. 파일 이름이 겹치지 않도록 고유한 이름(UUID) 생성
        String originalFilename = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFilename;

        // 2. S3(MinIO)에 업로드할 요청 객체 생성
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(uniqueFileName)
                .contentType(file.getContentType()) // 예: application/pdf
                .build();

        // 3. 파일 업로드 실행
        s3Client.putObject(putObjectRequest, 
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // 4. 업로드된 파일의 MinIO URL 반환 (예: http://localhost:9000/patentflow-bucket/파일명)
        return "http://localhost:9000/" + bucketName + "/" + uniqueFileName; 
    }
}