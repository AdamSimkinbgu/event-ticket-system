package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.support.BaseDomainTest;

class ProductionCompanyTest extends BaseDomainTest {

        private final int COMPANY_ID = 100;
        private final int OWNER_ID = 1;
        private final int MANAGER_ID = 2;
        private final int TARGET_USER_ID = 3;
        private final String COMPANY_1_NAME = "Company1";
        private final String COMPANY_1_DESCRIPTION = "A test production company1";
        private final double COMPANY_1_RATING = 4.5;

        private ProductionCompany company;
        private List<Permission> defaultPermissions;

        @BeforeEach
        public void setUp() {
                company = track(new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE,
                                COMPANY_1_DESCRIPTION, COMPANY_1_RATING));

                defaultPermissions = new ArrayList<>();
                defaultPermissions.add(Permission.APPOINT_MANAGER);
                defaultPermissions.add(Permission.MANAGE_INVENTORY);
        }

        @Test
        public void GivenValidOwnerAndTarget_WhenValidateManagerInvitation_ThenPendingManagerCreated() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                assertTrue(company.getPendingManagers().containsKey(TARGET_USER_ID));
        }

        @Test
        public void GivenPendingManager_WhenAcceptManagerInvitation_ThenManagerCreated() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.addManager(TARGET_USER_ID);

                assertTrue(company.getManagers().containsKey(TARGET_USER_ID));
        }

        @Test
        public void GivenPendingManager_WhenAcceptManagerInvitation_ThenPendingManagerRemoved() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.addManager(TARGET_USER_ID);

                assertFalse(company.getPendingManagers().containsKey(TARGET_USER_ID));
        }

        @Test
        public void GivenPendingManager_WhenRejectManagerInvitation_ThenPendingManagerRemoved() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.rejectManagerInvitation(TARGET_USER_ID);

                assertFalse(company.getPendingManagers().containsKey(TARGET_USER_ID));
        }

        @Test
        public void GivenManager_WhenRevokeManager_ThenManagerRemoved() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.addManager(TARGET_USER_ID);

                company.RevokeManager(TARGET_USER_ID);

                assertFalse(company.getManagers().containsKey(TARGET_USER_ID));
        }

        @Test
        public void GivenManager_WhenModifyManagerPermissions_ThenPermissionsUpdated() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.addManager(TARGET_USER_ID);

                List<Permission> newPermissions = new ArrayList<>();
                newPermissions.add(Permission.APPOINT_MANAGER);
                newPermissions.add(Permission.EDIT_POLICIES);

                company.ModifyManagerPermissions(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                newPermissions);

                assertEquals(newPermissions, company.getManagers().get(TARGET_USER_ID));
        }

        @Test
        public void GivenNullPermissions_WhenModifyManagerPermissions_ThenThrowException() {
                company.validateManagerInvitation(COMPANY_ID, TARGET_USER_ID, OWNER_ID, defaultPermissions);
                company.addManager(TARGET_USER_ID);

                assertThrows(RuntimeException.class,
                                () -> company.ModifyManagerPermissions(COMPANY_ID, TARGET_USER_ID, null));
        }

        @Test
        public void GivenInvalidCompanyId_WhenValidateManagerInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> company.validateManagerInvitation(
                                999,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions));
        }

        @Test
        public void GivenTargetIsOwner_WhenValidateManagerInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> company.validateManagerInvitation(
                                COMPANY_ID,
                                OWNER_ID,
                                OWNER_ID,
                                defaultPermissions));
        }

        @Test
        public void GivenEmptyPermissions_WhenValidateManagerInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                new ArrayList<>()));
        }

        @Test
        public void GivenNullPermissions_WhenValidateManagerInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                null));
        }

        @Test
        public void GivenPendingInvitationAlreadyExists_WhenValidateManagerInvitation_ThenThrowException() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                assertThrows(RuntimeException.class, () -> company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions));
        }

        @Test
        public void GivenTargetAlreadyManager_WhenValidateManagerInvitation_ThenThrowException() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.addManager(TARGET_USER_ID);

                assertThrows(RuntimeException.class, () -> company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions));
        }

        @Test
        public void GivenNoPendingInvitation_WhenAcceptManagerInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> company.addManager(TARGET_USER_ID));
        }

        @Test
        public void GivenNoPendingInvitation_WhenRejectManagerInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> company.rejectManagerInvitation(TARGET_USER_ID));
        }

        @Test
        public void GivenTargetIsNotManager_WhenRevokeManager_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> company.RevokeManager(TARGET_USER_ID));
        }

        @Test
        public void GivenTargetIsNotManager_WhenModifyManagerPermissions_ThenThrowException() {
                List<Permission> newPermissions = new ArrayList<>();
                newPermissions.add(Permission.EDIT_POLICIES);

                assertThrows(RuntimeException.class, () -> company.ModifyManagerPermissions(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                newPermissions));
        }

        @Test
        public void GivenEmptyPermissions_WhenModifyManagerPermissions_ThenThrowException() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.addManager(TARGET_USER_ID);

                assertThrows(RuntimeException.class, () -> company.ModifyManagerPermissions(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                new ArrayList<>()));
        }

        @Test
        public void GivenInvalidCompanyId_WhenModifyManagerPermissions_ThenThrowException() {
                company.validateManagerInvitation(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                OWNER_ID,
                                defaultPermissions);

                company.addManager(TARGET_USER_ID);

                List<Permission> newPermissions = new ArrayList<>();
                newPermissions.add(Permission.EDIT_POLICIES);

                assertThrows(RuntimeException.class, () -> company.ModifyManagerPermissions(
                                999,
                                TARGET_USER_ID,
                                newPermissions));
        }

        ////////////////////////////////////////////////////////////////// UC-20Tests

        @Test
        public void GivenManagerWithConfigureVenuePermission_WhenValidateManagerOrOwner_ThenDoesNotThrow() {
                company.validateManagerInvitation(COMPANY_ID, MANAGER_ID, OWNER_ID,
                                List.of(Permission.CONFIGURE_VENUE));
                company.addManager(MANAGER_ID);
                assertTrue(company.getManagers().get(MANAGER_ID).contains(Permission.CONFIGURE_VENUE));
        }

        @Test
        public void GivenOwner_WhenValidateManagerOrOwner_ThenDoesNotThrow() {
                assertTrue(company.getOwnerId() == OWNER_ID);
        }

        @Test
        public void GivenUserWithoutConfigureVenuePermission_WhenValidateManagerOrOwner_ThenThrowException() {

                company.validateManagerInvitation(COMPANY_ID, MANAGER_ID, OWNER_ID,
                                List.of(Permission.MANAGE_INVENTORY));
                company.addManager(MANAGER_ID);
                assertThrows(RuntimeException.class, () -> company.ValidateManagerOrOwnerForConfigureVenue(MANAGER_ID));
        }

        @Test
        public void GivenUserIsNotManagerOrOwner_WhenValidateManagerOrOwner_ThenThrowException() {
                assertThrows(RuntimeException.class,
                                () -> company.ValidateManagerOrOwnerForConfigureVenue(TARGET_USER_ID));
        }

        @Test
        public void GivenUserIsOwner_WhenCheckOwner_ThenSucceeds() {
                assertTrue(company.getOwnerId() == OWNER_ID);
        }

        @Test
        public void GivenUserIsNotOwner_WhenCheckOwner_ThenThrowException() {
                assertThrows(UnauthorizedActionException.class, () -> company.checkowner(TARGET_USER_ID));
        }

}
