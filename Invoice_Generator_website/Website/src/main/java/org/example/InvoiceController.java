package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/invoice")
public class InvoiceController {

    @GetMapping("/form")
    public RedirectView getForm() {
        return new RedirectView("/secondPage.html");
    }

    @GetMapping("/templateForm")
    public RedirectView getTemplateForm() {
        return new RedirectView("/Template_Invoice.html");
    }

    @GetMapping("/latest")
    public Invoice getLatestInvoice() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File("invoice.json"), Invoice.class);
    }

    @PostMapping("/uploadLogo")
    public String uploadLogo(@RequestParam("file") MultipartFile file) {
        if (!file.isEmpty()) {
            try {
                byte[] bytes = file.getBytes();
                Path path = Paths.get("java/resources/static/Logo1.jpg");
                Files.write(path, bytes);
                return "Logo uploaded successfully!";
            } catch (IOException e) {
                return "Failed to upload logo: " + e.getMessage();
            }
        } else {
            return "Logo file is empty.";
        }
    }


    @Autowired
    private ResourceLoader resourceLoader;

    @PostMapping("/generate")
    public void generateInvoice(@RequestBody Invoice invoice, HttpServletResponse response) throws IOException, WriterException {
        try {
            saveInvoiceToJsonFile(invoice);
        } catch (IOException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Failed to save invoice JSON: " + e.getMessage());
            return;
        }

        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        // Invoice Title
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
        contentStream.beginText();
        contentStream.newLineAtOffset(300, 750);
        contentStream.showText("INVOICE");
        contentStream.endText();

        // Invoice Details
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 720);
        contentStream.showText("Invoice Number: " + invoice.getInvoiceNumber());
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Company Name: " + invoice.getCompanyName());
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Bill From: " + invoice.getBillFrom());
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Bill To: " + invoice.getBillTo());
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Contact Number: " + invoice.getContactNumber());
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("GST Number: " + invoice.getGstNumber());
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Customer Name: " + invoice.getCustomerName());
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Invoice Date: " + invoice.getInvoiceDate());
        contentStream.endText();

        // QR Code
        String qrCodeURL = "https://example.com/invoice/" + invoice.getInvoiceNumber();
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeURL, BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        byte[] pngData = pngOutputStream.toByteArray();
        PDImageXObject qrCodeImage = PDImageXObject.createFromByteArray(document, pngData, "QRCode");
        contentStream.drawImage(qrCodeImage, 475, 520, 100, 100);

        // Table Header
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 500);
        contentStream.showText("Description");
        contentStream.newLineAtOffset(200, 0);
        contentStream.showText("Quantity");
        contentStream.newLineAtOffset(100, 0);
        contentStream.showText("Unit Price");
        contentStream.newLineAtOffset(100, 0);
        contentStream.showText("Total");
        contentStream.endText();

        // Table Content
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        int yOffset = 480;
        for (Item item : invoice.getItems()) {
            contentStream.beginText();
            contentStream.newLineAtOffset(50, yOffset);
            contentStream.showText(item.getDescription());
            contentStream.newLineAtOffset(200, 0);
            contentStream.showText(String.valueOf(item.getQuantity()));
            contentStream.newLineAtOffset(100, 0);
            contentStream.showText(String.valueOf(item.getUnitPrice()));
            contentStream.newLineAtOffset(100, 0);
            contentStream.showText(String.valueOf(item.getTotal()));
            contentStream.endText();
            yOffset -= 20;
        }

        // Total Amount
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(350, yOffset - 20);

        // Calculate total amount
        double totalAmount = 0;
        for (Item item : invoice.getItems()) {
            totalAmount += item.getTotal();
        }
        contentStream.showText("Total: " + totalAmount);
        contentStream.endText();

        contentStream.close();

        // Set the response content type and headers
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=invoice.pdf");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.save(outputStream);
        document.close();

        // Send the PDF as a response
        response.getOutputStream().write(outputStream.toByteArray());
    }

    @PostMapping("/preview")
    public void previewInvoice(@RequestBody Invoice invoice, HttpServletResponse response) throws IOException, WriterException {
        generateInvoicePDF(invoice, response, "inline; filename=invoice_preview.pdf");
    }

    private List<String> getLines(String text, float columnWidth, PDFont font, float fontSize) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (font.getStringWidth(line + word) / 1000 * fontSize > columnWidth) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            line.append(word).append(" ");
        }
        lines.add(line.toString());
        return lines;
    }

    public void generateInvoicePDF(Invoice invoice, HttpServletResponse response, String contentDisposition) throws IOException, WriterException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        // Header
        Resource resource = resourceLoader.getResource("classpath:static/Logo2.jpg");
        try (InputStream inputStream = resource.getInputStream()) {
            if (inputStream != null) {
                PDImageXObject image = JPEGFactory.createFromStream(document, inputStream);
                contentStream.drawImage(image, 15, 745, image.getWidth() / 2, image.getHeight() / 2);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Invoice Title
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
        contentStream.beginText();
        contentStream.newLineAtOffset(300, 757);
        contentStream.showText("INVOICE");
        contentStream.endText();

        contentStream.setLineWidth(2);
        contentStream.setStrokingColor(0, 0, 0);
        contentStream.moveTo(10, 730);
        contentStream.lineTo(600, 733);
        contentStream.stroke();

        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 710);
        contentStream.showText("Invoice Number: " + invoice.getInvoiceNumber());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Company Name: " + invoice.getCompanyName());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("GST Number: " + invoice.getGstNumber());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Customer Name: " + invoice.getCustomerName());

        float startX = 0;
        float billFieldWidth = (page.getMediaBox().getWidth() - 2 * startX) / 3;

        contentStream.newLineAtOffset(startX, -55);
        contentStream.showText("Bill From: ");
        String[] billFromLines = invoice.getBillFrom().split(",");
        for (String line : billFromLines) {
            contentStream.newLineAtOffset(0, -16);
            contentStream.showText(line + ",");
        }

        contentStream.newLineAtOffset(startX + billFieldWidth + 20, 75);
        contentStream.showText("Bill To: ");
        String[] billToLines = invoice.getBillTo().split(",");
        for (String line : billToLines) {
            contentStream.newLineAtOffset(0, -17);
            contentStream.showText(line + ",");
        }

        contentStream.newLineAtOffset(0, -50);
        contentStream.showText("Contact Number: " + invoice.getContactNumber());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Invoice Date: " + invoice.getInvoiceDate());
        contentStream.endText();

        // QR Code
        String qrCodeURL = "http://localhost:8080";
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeURL, BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        byte[] pngData = pngOutputStream.toByteArray();
        PDImageXObject qrCodeImage = PDImageXObject.createFromByteArray(document, pngData, "QRCode");
        contentStream.drawImage(qrCodeImage, 475, 600, 100, 100);

        // Table Header
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 400);
        contentStream.showText("Description");
        contentStream.newLineAtOffset(200, 0);
        contentStream.showText("Quantity");
        contentStream.newLineAtOffset(100, 0);
        contentStream.showText("Unit Price");
        contentStream.newLineAtOffset(100, 0);
        contentStream.showText("Total");
        contentStream.endText();

        // Table Content
        int pgCount = 1;
        int totalPages = calculateTotalPages(invoice);
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        int yOffset = 380;
        for (Item item : invoice.getItems()) {
            if (yOffset <= 100) {
                contentStream.close();

                page = new PDPage();
                document.addPage(page);
                contentStream = new PDPageContentStream(document, page);
                pgCount++;

                // Header
                try (InputStream inputStream = resource.getInputStream()) {
                    if (inputStream != null) {
                        PDImageXObject image = JPEGFactory.createFromStream(document, inputStream);
                        contentStream.drawImage(image, 15, 745, image.getWidth() / 2, image.getHeight() / 2);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Invoice Title
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.beginText();
                contentStream.newLineAtOffset(300, 760);
                contentStream.showText("INVOICE");
                contentStream.endText();

                contentStream.setLineWidth(2);
                contentStream.setStrokingColor(0, 0, 0);
                contentStream.moveTo(10, 730);
                contentStream.lineTo(600, 733);
                contentStream.stroke();

                // Table Header on new page
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Description");
                contentStream.newLineAtOffset(200, 0);
                contentStream.showText("Quantity");
                contentStream.newLineAtOffset(100, 0);
                contentStream.showText("Unit Price");
                contentStream.newLineAtOffset(100, 0);
                contentStream.showText("Total");
                contentStream.endText();

                yOffset = 670;
            }
            contentStream.setFont(PDType1Font.HELVETICA, 10);

            // Wrap text for the description
            float descriptionWidth = 200;
            List<String> descriptionLines = getLines(item.getDescription(), descriptionWidth, PDType1Font.HELVETICA, 10);
            for (String line : descriptionLines) {
                contentStream.beginText();
                contentStream.newLineAtOffset(50, yOffset);
                contentStream.showText(line);
                contentStream.endText();
                yOffset -= 12;
            }

            // Adjust yOffset for other columns
            float maxLineHeight = descriptionLines.size() * 12;

            contentStream.beginText();
            contentStream.newLineAtOffset(250, yOffset + maxLineHeight);
            contentStream.showText(String.valueOf(item.getQuantity()));

            contentStream.newLineAtOffset(100, 0);
            contentStream.showText(String.valueOf(item.getUnitPrice()));

            contentStream.newLineAtOffset(100, 0);
            contentStream.showText(String.valueOf(item.getTotal()));
            contentStream.endText();

            yOffset -= 20;

            addPageNumber(contentStream, totalPages, pgCount);
        }

        // Total Amount
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(350, yOffset - 20);

        // Calculate total amount
        double totalAmount = 0;
        for (Item item : invoice.getItems()) {
            totalAmount += item.getTotal();
        }
        contentStream.showText("Total: " + totalAmount);
        contentStream.endText();

        contentStream.close();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.save(outputStream);
        document.close();

        // Set the response content type and headers for inline viewing
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", contentDisposition);

        // Send the PDF as a response
        response.getOutputStream().write(outputStream.toByteArray());
    }


    private void addPageNumber(PDPageContentStream contentStream, int totalPages, int currentPage) throws IOException {
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(550, 20); // Adjust the position as needed
        contentStream.showText(currentPage + "/" + totalPages);
        contentStream.endText();
    }

    private int calculateTotalPages(Invoice invoice) {
        int itemsPerPage = 14;
        int totalItems = invoice.getItems().size();
        return (int) Math.ceil((double) totalItems / itemsPerPage);
    }

    @PostMapping("/previewTemplate")
    public void previewTemplateInvoice(@RequestBody Invoice invoice, HttpServletResponse response) throws IOException, WriterException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        //Header Section:
        contentStream.setLineWidth(1);
        contentStream.setStrokingColor(0, 0, 0); // Set the stroke color to black
        contentStream.addRect(10, page.getMediaBox().getHeight() - 60, 50, 50);
        contentStream.stroke();

        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(10, 778);
        contentStream.showText("your");
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("company");
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("logo");
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("here");
        contentStream.endText();

        // Invoice Title
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
        contentStream.beginText();
        contentStream.newLineAtOffset(300, 750);
        contentStream.showText("INVOICE");
        contentStream.endText();

        contentStream.setLineWidth(2);
        contentStream.setStrokingColor(0, 0, 0);
        contentStream.moveTo(10, 730);
        contentStream.lineTo(600, 733);
        contentStream.stroke();

        // Invoice Details
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 700);
        contentStream.showText("Invoice Number: " + invoice.getInvoiceNumber());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Company Name: " + invoice.getCompanyName());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Customer Name: " + invoice.getCustomerName());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Contact Number: " + invoice.getContactNumber());


        float startX = 0;
        float billFieldWidth = (page.getMediaBox().getWidth() - 2 * startX) / 3;

        contentStream.newLineAtOffset(startX, -20);
        contentStream.showText("Bill From: ");
        String[] billFromLines = invoice.getBillFrom().split(",");
        for (String line : billFromLines) {
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText(line + ",");
        }

        contentStream.newLineAtOffset(startX + billFieldWidth, -20);
        contentStream.showText("Bill To: ");
        String[] billToLines = invoice.getBillTo().split(",");
        for (String line : billToLines) {
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText(line + ",");
        }

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("GST Number: " + invoice.getGstNumber());

        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Invoice Date: " + invoice.getInvoiceDate());
        contentStream.endText();

        // QR Code
        String qrCodeURL = "https://example.com/invoice/" + invoice.getInvoiceNumber();
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeURL, BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        byte[] pngData = pngOutputStream.toByteArray();
        PDImageXObject qrCodeImage = PDImageXObject.createFromByteArray(document, pngData, "QRCode");
        contentStream.drawImage(qrCodeImage, 475, 520, 100, 100);

        // Table Header
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 400);
        contentStream.showText("Description");
        contentStream.newLineAtOffset(200, 0);
        contentStream.showText("Quantity");
        contentStream.newLineAtOffset(100, 0);
        contentStream.showText("Unit Price");
        contentStream.newLineAtOffset(100, 0);
        contentStream.showText("Total");
        contentStream.endText();

        // Table Content
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        int yOffset = 380;
        for (Item item : invoice.getItems()) {
            contentStream.beginText();
            contentStream.newLineAtOffset(50, yOffset);
            contentStream.showText(item.getDescription());
            contentStream.newLineAtOffset(200, 0);
            contentStream.showText(String.valueOf(item.getQuantity()));
            contentStream.newLineAtOffset(100, 0);
            contentStream.showText(String.valueOf(item.getUnitPrice()));
            contentStream.newLineAtOffset(100, 0);
            contentStream.showText(String.valueOf(item.getTotal()));
            contentStream.endText();
            yOffset -= 20;
        }

        // Total Amount
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(350, yOffset - 20);

        // Calculate total amount
        double totalAmount = 0;
        for (Item item : invoice.getItems()) {
            totalAmount += item.getTotal();
        }
        contentStream.showText("Total: " + totalAmount);
        contentStream.endText();

        contentStream.close();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.save(outputStream);
        document.close();

        // Set the response content type and headers for inline viewing
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=invoice_preview.pdf");

        // Send the PDF as a response
        response.getOutputStream().write(outputStream.toByteArray());
    }

    private void saveInvoiceToJsonFile(Invoice invoice) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new File("invoice.json"), invoice);
    }
}

class Invoice {
    private String companyName;
    private String invoiceNumber;
    private String billFrom;
    private String billTo;
    private String contactNumber;
    private String gstNumber;
    private String customerName;
    private String invoiceDate;
    private List<Item> items;

    // Getters and setters

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getBillFrom() {
        return billFrom;
    }

    public void setBillFrom(String billFrom) {
        this.billFrom = billFrom;
    }

    public String getBillTo() {
        return billTo;
    }

    public void setBillTo(String billTo) {
        this.billTo = billTo;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(String invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }
}

class Item {
    private String description;
    private int quantity;
    private double unitPrice;
    private double total;

    // Getters and setters

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }
}