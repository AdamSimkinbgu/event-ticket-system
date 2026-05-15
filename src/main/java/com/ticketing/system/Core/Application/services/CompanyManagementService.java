package com.ticketing.system.Core.Application.services;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.ManagementInvitation;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import java.util.regex.Pattern;

public class CompanyManagementService {
    private final IProductionCompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final ISessionManager sessionManager;
    private final Logger logger = LoggerFactory.getLogger(CompanyManagementService.class);
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String PHONE_PATTERN = "^\\+?[0-9\\-\\s]{9,15}$";


    public CompanyManagementService(IProductionCompanyRepository companyRepository, IUserRepository userRepository, ISessionManager sessionManager) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.sessionManager = sessionManager;
    }

    public void inviteManager(String token, int companyId, int targetId, List<Permission> permissions) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for inviting manager");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.validateManagerInvitation(companyId, targetId, ownerId, permissions);
        
        targetUser.InvitetoCompanyAppointment(companyId, ownerId, permissions);

        companyRepository.updateCompany(company);
        userRepository.updateUser(targetUser);

        logger.info("user invited succesfully");

    }

    public void acceptManagerInvitation(String token, int companyId) {
        if (!sessionManager.validateToken(token)) {
                logger.warn("Invalid token provided for accepting manager invitation");
            throw new RuntimeException("Invalid token");
        }
        int targetId = sessionManager.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        ManagementInvitation invitation = targetUser.acceptInvitation(companyId);
        company.acceptManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        logger.info("Manager invitation accepted successfully");
    }

    public void rejectManagerInvitation(String token, int companyId) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for rejecting manager invitation");
            throw new RuntimeException("Invalid token");
        }
        int targetId = sessionManager.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        targetUser.rejectInvitation(companyId);
        company.rejectManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        logger.info("Manager invitation rejected successfully");
    }

    public void RevokeManager(String token, int companyId, int targetId) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for revoking manager");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);

        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.RevokeManager(targetId);
        targetUser.removeCompanyAppointment(companyId);

        
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        logger.info("Manager revoked successfully");

    }

    public void ModifyManagerPermissions(String token, int companyId, int targetId, List<Permission> newPermissions) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for modifying manager permissions");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.ModifyManagerPermissions(companyId, targetId, newPermissions);
        targetUser.ModifyManagerPermissions(companyId, targetId, newPermissions);

         userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);

        logger.info("Manager permissions modified successfully");
    }

    // ---------------------------------------------------------------------------
    // DTO-typed methods added in skeleton round (parallel to the existing
    // token-arg / List<Permission>-arg methods above; team to consolidate later).
    // ---------------------------------------------------------------------------

    // UC-18 — register a new Production Company; appoints Founder/Owner in same transaction.
    public ProductionCompanyDTO registerCompany(String token, CompanyRegistrationDTO request) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for registering a company");
            throw new RuntimeException("Invalid token"); 
        }
        int userId = sessionManager.extractUserId(token);

        if (request.name() == null || request.name().trim().isEmpty() ||
            request.description() == null || request.description().trim().isEmpty()) {
            
            logger.warn("Company registration failed: Missing required fields by user {}", userId);
            throw new IllegalArgumentException("All company fields (name, description, email, phone) must be provided");
        }

        if (IProductionCompanyRepository.existsByName(request.name().trim())) {
            logger.warn("Company registration failed: Company name '{}' already exists", request.name());
            throw new IllegalStateException("A company with this name already exists");
        }
        
        try {
            int CompanyId = IProductionCompanyRepository.nextId();
            ProductionCompany newProductionCompany = new ProductionCompany(
                CompanyId,
                userId,
                request.name().trim(),
                ACTIVE,
                request.description().trim(),
                null
            );

            Company savedCompany = IProductionCompanyRepository.save(newProductionCompany);
            logger.info("Successfully registered new company: '{}' by userId: {}", savedCompany.getName(), userId);

            return new ProductionCompanyDTO(
                savedCompany.getId(),
                savedCompany.getName(),
                savedCompany.getDescription(),
                savedCompany.getStatus(),
                savedCompany.getOwnerId()
            );

        } catch (Exception e) {
            logger.error("Error occurred while saving company '{}': {}", request.name(), e.getMessage());
            throw new RuntimeException("Failed to register company due to a server error", e);
        }
    }

    // UC-23 — Owner appoints another Member as co-Owner (PENDING).
    public void appointOwner(
            String token,
            com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO request) {
        throw new UnsupportedOperationException("UC-23: not implemented");
    }

    // UC-23 / UC-24 — target accepts or rejects a pending appointment.
    public void respondToAppointment(
            String token,
            com.ticketing.system.Core.Application.dto.AppointmentResponseDTO response) {
        throw new UnsupportedOperationException("UC-23 / UC-24: not implemented");
    }

    // UC-24 — Owner appoints a Manager with explicit granular permissions.
    public void appointManager(
            String token,
            com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO request) {
        throw new UnsupportedOperationException("UC-24: not implemented");
    }

    // UC-24 — edit a Manager's permission set (only by the original appointer).
    public void editManagerPermissions(
            String token,
            com.ticketing.system.Core.Application.dto.PermissionEditDTO edit) {
        throw new UnsupportedOperationException("UC-24: not implemented");
    }

    // UC-21 — set / replace company-wide default policies.
    public void setCompanyPolicies(
            String token,
            com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO config) {
        throw new UnsupportedOperationException("UC-21: not implemented");
    }

    // UC-22 — Owner-side flat list of company sales.
    public com.ticketing.system.Core.Application.dto.PageDTO<
            com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO>
            viewSalesHistory(String token, int companyId, int pageNumber, int pageSize) {
        throw new UnsupportedOperationException("UC-22: not implemented");
    }

    // UC-25 — recursive organizational tree (Owners only per II.4.15).
    public com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO
            viewOrganizationalTree(String token, int companyId) {
        throw new UnsupportedOperationException("UC-25: not implemented");
    }

}
