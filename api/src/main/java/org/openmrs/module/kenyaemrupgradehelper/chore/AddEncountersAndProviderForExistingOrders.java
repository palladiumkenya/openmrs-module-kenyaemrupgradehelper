package org.openmrs.module.kenyaemrupgradehelper.chore;

import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyacore.chore.AbstractChore;
import org.openmrs.module.kenyaemr.api.KenyaEmrService;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
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
        ProviderService providerService = Context.getProviderService();

        EncounterService encounterService = Context.getEncounterService();
        EncounterRole encounterRole = encounterService.getEncounterRoleByUuid("a0b03050-c99b-11e0-9572-0800200c9a66");
        Provider unknownProvider = providerService.getProviderByUuid("ae01b8ff-a4cc-4012-bcf7-72359e852e14");

        User drugOrderer = Context.getUserService().getUserByUsername("unknown_provider");
        for(Patient patient : allPatients) {
            List<DrugOrder> allOrdersForAPatient = Context.getOrderService().getDrugOrdersByPatient(patient);
            for (DrugOrder drugOrder : allOrdersForAPatient) {
                drugOrder.setOrderer(drugOrder.getCreator());

                //if(drugOrder.getEncounter() == null || drugOrder.getEncounter().equals(null)) {
                Encounter encounter = new Encounter();
                encounter.setEncounterDatetime(drugOrder.getStartDate());
                encounter.setPatient(patient);
                encounter.setLocation(Context.getService(KenyaEmrService.class).getDefaultLocation());
                encounter.setForm(MetadataUtils.existing(Form.class, HivMetadata._Form.DRUG_ORDER));
                encounter.setEncounterType(MetadataUtils.existing(EncounterType.class, HivMetadata._EncounterType.DRUG_ORDER));
                encounter.setCreator(drugOrder.getCreator());
                encounter.setProvider(encounterRole, unknownProvider);
                encounter.setDateCreated(drugOrder.getDateCreated());
                Context.getEncounterService().saveEncounter(encounter);

                // }
                drugOrder.setEncounter(encounter);
                Context.getOrderService().saveOrder(drugOrder);
            }

        }
    }
}
