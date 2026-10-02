package com.saas.billing.controller;

import com.saas.billing.dto.InvoiceResponse;
import com.saas.billing.dto.TenantPlanResponse;
import com.saas.billing.service.InvoiceService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing")
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/invoices/generate")
    public ResponseEntity<InvoiceResponse> generate(
            @RequestParam("period") String period, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.generate(tenantId(jwt), period));
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return invoiceService.list(tenantId(jwt));
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse get(@PathVariable("invoiceId") UUID invoiceId, @AuthenticationPrincipal Jwt jwt) {
        return invoiceService.get(tenantId(jwt), invoiceId);
    }

    @GetMapping("/plan")
    public TenantPlanResponse plan(@AuthenticationPrincipal Jwt jwt) {
        return invoiceService.getPlan(tenantId(jwt));
    }

    private UUID tenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenantId"));
    }
}