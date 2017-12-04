package org.openmrs.module.kenyaemrupgradehelper.chore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * To migrate to 1.10.x it is a requirement that each drug order should be associated with a provider and an encounter
 */
/*@Component("kenyaemrupgradehelper.chore.AddEncountersAndProviderForExistingOrders")*/
public class AddEncountersAndProviderForExistingOrders extends AbstractChore {

    // Logger
    protected final Log log = LogFactory.getLog(getClass());
    /**
     * @see org.openmrs.module.kenyacore.chore.AbstractChore#perform(java.io.PrintWriter)
     */
    @Override
    public void perform(PrintWriter out) {
        PatientService patientService = Context.getPatientService();
        List<Patient> allPatients = patientService.getAllPatients();
        User drugOrderer = Context.getUserService().getUserByUsername("upgradeUser");
        if (drugOrderer == null)
            drugOrderer = createProviderForOrders();

        //processAll(allPatients, drugOrderer, allPatients.size(),out );
        fillProvidersAndEncountersForDrugOrders(allPatients, out);
    }

    private void fillProvidersAndEncountersForDrugOrders(List<Patient> allPatients, PrintWriter out) {
        // first check to confirm if the user already exists
        User drugOrderer = Context.getUserService().getUserByUsername("upgradeUser");
        if (drugOrderer == null)
            drugOrderer = createProviderForOrders();

        for (Patient patient : allPatients) {
            out.println("Processing orders for: " + patient.getGivenName());
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

        /*Provider provider = new Provider();
        provider.setPerson(createdUser.getPerson());
        provider.setIdentifier(createdUser.getSystemId());
        Context.getProviderService().saveProvider(provider);*/

        return createdUser;
    }

    public void processAll(List<Patient> patients, final User drugOrderer, int numThreads,final PrintWriter out) {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for(final Patient patient : patients) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    // doProcessing(patient, drugOrderer, out);
                    out.println("Processing order: " + patient.getGivenName());
                }
            });
        }
        // instead of a loop, you could also use ExecutorService#invokeAll()
        // with the passed-in list if Endpoint implements
        // java.util.concurrent.Callable

        executor.shutdown();
    }

    private void doProcessing(Patient patient, User drugOrderer, PrintWriter out) {
        out.println("Processing orders for patient: " + patient.getGivenName());
        try {
        List<DrugOrder> allOrdersForAPatient = Context.getOrderService().getDrugOrdersByPatient(patient, OrderService.ORDER_STATUS.ANY, true);
        for (DrugOrder drugOrder : allOrdersForAPatient) {
            // set orderer
            drugOrder.setOrderer(drugOrderer);
            out.println("===== In order : " + drugOrder.getId());

            // fix discontinuation details

            if (drugOrder.getDiscontinued()) {
                if(drugOrder.getDiscontinuedBy() == null) {
                    drugOrder.setDiscontinuedBy(drugOrderer);
                }

                if(drugOrder.getDiscontinuedDate() == null) {
                    drugOrder.setDiscontinuedDate(new Date());
                }
            }

            out.println("========== Checked discontinuation : " + drugOrder.getId());
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
            out.println("Processed order:=== " + drugOrder.getId() + " for " + patient.getGivenName());
            //out.println("Successfully processed order: " + drugOrder.getId() + " ===> Encounter ID: " + encounter.getId());

        }} catch (Exception e) {
            e.printStackTrace();
            e.getCause();
        }
        out.println("Completed Processing orders for patient: " + patient.getGivenName());
    }
}
