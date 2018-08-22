DELIMITER $$
DROP PROCEDURE IF EXISTS sp_process_drug_orders$$
CREATE PROCEDURE sp_process_drug_orders(IN providerName VARCHAR(100))
BEGIN

  DECLARE no_more_rows BOOLEAN;
  DECLARE record_uuid VARCHAR(100);
  DECLARE v_row_count INT(11);
  DECLARE userID INT(11);
  DECLARE encounterTypeID INT(11);
  DECLARE formID INT(11);

  DECLARE existing_drug_orders CURSOR FOR
    SELECT uuid FROM orders;

  DECLARE CONTINUE HANDLER FOR NOT FOUND
    SET no_more_rows = TRUE;

  OPEN existing_drug_orders;
  SET v_row_count = FOUND_ROWS();

  SELECT user_id INTO userID from users where username=providerName;
  SELECT encounter_type_id INTO encounterTypeID from encounter_type where uuid="7df67b83-1b84-4fe2-b1b7-794b4e9bfcc3";
  SELECT form_id INTO formID from form where uuid="888dbabd-1c18-4653-82c2-e753415ab79a";

  IF v_row_count > 0 THEN
    get_enrollment_record: LOOP
    FETCH existing_drug_orders INTO record_uuid;

    IF no_more_rows THEN
      CLOSE existing_drug_orders;
      LEAVE get_enrollment_record;
    END IF;

    CALL sp_create_order_encounter(record_uuid, userID, encounterTypeID, formID);

  END LOOP get_enrollment_record;
  ELSE
    update orders set discontinued_by=userID where discontinued=1 and discontinued_by is null;
    update orders set discontinued_date=curdate() where discontinued=1 and discontinued_date is null;
    SELECT "NO ROWS WERE FOUND";
  END IF;

END
$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_create_order_encounter$$
CREATE PROCEDURE sp_create_order_encounter(IN recordUUID VARCHAR(100), IN providerID INT(11), IN encounterTypeID INT(11) , IN formID INT(11))
BEGIN
  DECLARE exec_status INT(11) DEFAULT 1;
  DECLARE creatorID INT(11);
  DECLARE patientID INT(11);
  DECLARE encounterDate DATETIME;
  DECLARE encounterID INT(11);
  DECLARE discontinued INT(11);


  DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
      SET exec_status = -1;
      ROLLBACK;
    END;
  -- perform all procedure calls within a transaction
  START TRANSACTION;

  SELECT creator, patient_id, date_created, discontinued INTO creatorID, patientID, encounterDate, discontinued from orders where uuid=recordUUID;



  insert into encounter (encounter_type, patient_id, form_id, encounter_datetime, creator, date_created)
  values (encounterTypeID, patientID, formID, encounterDate, creatorID, encounterDate) ;

  SET encounterID = LAST_INSERT_ID();
  UPDATE orders set orderer=providerID, encounter_id=encounterID where uuid=recordUUID;

  IF discontinued = 1 THEN
    UPDATE orders set discontinued_date=CURDATE(), discontinued_by=providerID where uuid=recordUUID;
  END IF;

  COMMIT;

END;
  $$
DELIMITER ;

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_effect_db_changes_on_orders$$
CREATE PROCEDURE sp_effect_db_changes_on_orders()
  BEGIN

    ALTER TABLE `order_type`
      ADD COLUMN `java_class_name` VARCHAR(255) DEFAULT NULL,
      ADD COLUMN `parent` INT(11) DEFAULT NULL;

    UPDATE `order_type` SET `java_class_name` = 'org.openmrs.TestOrder' WHERE name ='LAB TEST ORDERS';
    UPDATE  order_type SET java_class_name = 'org.openmrs.DrugOrder' WHERE   name='Drug Order';
    -- UPDATE drug_order set route=160240; -- previous orders never filled this one. should be run after upgrade is complete since route was introduced in later versions
    -- this is required for the upgrade
    insert into global_property(property, property_value, description, uuid)
    values("order.drugDosingUnitsConceptUuid", "162384AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Drug dosing units concept", 'a8ff078c-9b1b-11e8-98d0-529269fb1459');

    -- set all null dosing unit and frequencies appropriately. This will avert upgrade from stalling
    UPDATE drug_order SET frequency='OD' where frequency is null OR UPPER(frequency)='NULL';
    UPDATE drug_order SET units='tab' where units is null OR UPPER(units)='NULL';


  END
$$
DELIMITER ;


-- Procedure for upgrading
-- Create a user with provider role using the name : upgradeManager
call sp_effect_db_changes_on_orders();
call sp_process_drug_orders("upgradeManager");

