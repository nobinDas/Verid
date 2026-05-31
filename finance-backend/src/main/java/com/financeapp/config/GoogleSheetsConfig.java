package com.financeapp.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Configuration
@ConditionalOnProperty(name = "app.google.credentials-path")
public class GoogleSheetsConfig {

    @Value("${app.google.credentials-path}")
    private String credentialsPath;

    @Bean
    public Sheets googleSheets() throws IOException, GeneralSecurityException {
        GoogleCredentials credentials;
        try (FileInputStream stream = new FileInputStream(credentialsPath)) {
            credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(SheetsScopes.SPREADSHEETS);
        }
        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("VeridFinanceApp")
                .build();
    }
}
