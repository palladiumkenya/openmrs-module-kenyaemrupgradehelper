package org.openmrs.module.kenyaemrupgradehelper.chore;

import org.openmrs.*;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyacore.chore.AbstractChore;
import org.openmrs.module.kenyaemr.api.KenyaEmrService;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

/**
 * To migrate to 1.10.x it is a requirement that each drug order should be associated with a provider and an encounter
 */
@Component("kenyaemr.chore.AddEncountersAndProviderForExistingOrders")
public class AddEncountersAndProviderForExistingOrders extends AbstractChore {


    /**
     * @see org.openmrs.module.kenyacore.chore.AbstractChore#perform(java.io.PrintWriter)
     */
    @Override
    public void perform(PrintWriter out) {
        PatientService patientService = Context.getPatientService();
        List<Patient> allPatients = patientService.getAllPatients();
        fillProvidersAndEncountersForDrugOrders(allPatients, out);
    }

    private void fillProvidersAndEncountersForDrugOrders(List<Patient> allPatients, PrintWriter out) {
        // first check to confirm if the user already exists
        User drugOrderer = Context.getUserService().getUserByUsername("upgradeUser");
        if (drugOrderer == null)
            drugOrderer = createProviderForOrders();

        for (Patient patient : allPatients) {
            List<DrugOrder> allOrdersForAPatient = Context.getOrderService().getDrugOrdersByPatient(patient, OrderService.ORDER_STATUS.ANY, true);
            for (DrugOrder drugOrder : allOrdersForAPatient) {
                // set orderer
                drugOrder.setOrderer(drugOrderer);

                // fix discontinuation details

                if (drugOrder.getDiscontinued()) {
                    if(drugOrder.getDiscontinuedBy() == null) {
                        drugOrder.setDiscontinuedBy(drugOrderer);
                    }

                    if(drugOrder.getDiscontinuedDate() == null) {
                        drugOrder.setDiscontinuedDate(new Date());
                    }
                }

                // create drug order encounter for order
                Encounter encounter = new Encounter();
                encounter.setEncounterDatetime(drugOrder.getStartDate());
                encounter.setPatient(patient);
                encounter.setLocation(Context.getService(KenyaEmrService.class).getDefaultLocation());
                encounter.setForm(MetadataUtils.existing(Form.class, HivMetadata._Form.DRUG_ORDER));
                encounter.setEncounterType(MetadataUtils.existing(EncounterType.class, HivMetadata._EncounterType.DRUG_ORDER));
                encounter.setCreator(drugOrder.getCreator());
                encounter.setDateCreated(drugOrder.getDateCreated());
                Context.getEncounterService().saveEncounter(encounter);

                drugOrder.setEncounter(encounter);
                Context.getOrderService().saveOrder(drugOrder);
                out.println("Successfully processed order: " + drugOrder.getId() + " ===> Encounter ID: " + encounter.getId());
            }

        }
    }

    private User createProviderForOrders() {
        UserService us = Context.getUserService();
        User u = new User();
        u.setPerson(new Person());

        Role clinician = us.getRole("Clinician");
        u.addName(new PersonName("Orders", "Upgrade", "User"));
        u.setUsername("upgradeUser");
        u.getPerson().setGender("M");
        u.addRole(clinician);
        User createdUser = us.saveUser(u, "Openmr5xy");
        return createdUser;
    }
}
