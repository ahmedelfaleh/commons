-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_master
-- Table Name 	: master.device_provider
-- Purpose    	: Device Provider : To store MOSIP device provider details, Devices will be used in MOSIP those are provided by only registred device provider.
--           
-- Create By   	: Sadanandegowda
-- Created Date	: 04-Oct-2019
-- 
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- 
-- ------------------------------------------------------------------------------------------
-- object: master.device_provider | type: TABLE --
-- DROP TABLE IF EXISTS master.device_provider CASCADE;
CREATE TABLE master.device_provider(
	id character varying(36) NOT NULL,
	vendor_name character varying(128) NOT NULL,
	address character varying(512),
	email character varying(256),
	contact_number character varying(16),
	certificate_alias character varying(36),
	is_active boolean NOT NULL,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	is_deleted boolean,
	del_dtimes timestamp,
	CONSTRAINT pk_devprd_id PRIMARY KEY (id)

);
-- ddl-end --
COMMENT ON TABLE master.device_provider IS 'Device Provider : To store MOSIP device provider details, Devices will be used in MOSIP those are provided by only registred device provider.';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.id IS 'Device Provider ID: Unique ID of the device provider, Device provider id is generated by MOSIP system.';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.vendor_name IS 'Device Provider Vendor Name : Name of the device provider, Those device will be used by MOSIP syatem.';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.address IS 'Address : Postal address of the device provider, This will have full address of the provider including postal code.';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.email IS 'Email Address : Email address of the device provider';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.contact_number IS 'Contact Number : Contact number of the device provider';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.certificate_alias IS 'Certificate Alias : Its certificate alias which is stored in HSM and provided by MOSIP to a device provider';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.is_active IS 'IS_Active : Flag to mark whether the record/device is Active or In-active';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.cr_by IS 'Created By : ID or name of the user who create / insert record';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.upd_by IS 'Updated By : ID or name of the user who update the record with new values';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.is_deleted IS 'IS_Deleted : Flag to mark whether the record is Soft deleted.';
-- ddl-end --
COMMENT ON COLUMN master.device_provider.del_dtimes IS 'Deleted DateTimestamp : Date and Timestamp when the record is soft deleted with is_deleted=TRUE';
-- ddl-end --