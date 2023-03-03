ALTER TABLE form_element DROP CONSTRAINT form_element_form_element_group_id_display_order_organisati_key;
ALTER TABLE form_element_group DROP CONSTRAINT form_element_group_form_id_display_order_organisation_id_key;

ALTER TABLE form_element ADD CONSTRAINT fe_feg_id_display_order_org_id_is_voided_key UNIQUE (form_element_group_id, display_order, organisation_id, is_voided) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE form_element_group ADD CONSTRAINT feg_f_id_display_order_org_id_is_voided_key UNIQUE (form_id, display_order, organisation_id, is_voided) DEFERRABLE INITIALLY DEFERRED;