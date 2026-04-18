package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.PayslipResponse;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PdfService — Handles generation of PDFs from HTML templates.
 * Uses Thymeleaf for templating and OpenHTMLtoPDF for rendering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfService {

    private final TemplateEngine templateEngine;
    private final SystemService systemService;

    /**
     * Converts a PayslipResponse DTO into a native PDF byte array.
     */
    public byte[] generatePayslipPdf(PayslipResponse payslip) {
        log.info("Generating PDF for Payslip Record ID: {}", payslip.getRecordId());

        // 1. Prepare Thymeleaf Context
        Context context = new Context();
        context.setVariable("payslip", payslip);
        
        // Inject Dynamic Branding
        context.setVariable("companyName", systemService.getCompanyName());
        context.setVariable("companyLogo", systemService.getCompanyLogoBase64());

        // 2. Render HTML string
        String htmlContent = templateEngine.process("payslip", context);

        // 3. Convert HTML to PDF using OpenHTMLtoPDF
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Important for formatting to render accurately
            builder.withHtmlContent(htmlContent, "classpath:/templates/");
            builder.toStream(os);
            builder.run();

            return os.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF for payslip ID: {}", payslip.getRecordId(), e);
            throw new RuntimeException("Error generating PDF document: " + e.getMessage(), e);
        }
    }
}
