package io.mosip.kernel.masterdata.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.transaction.Transactional;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.dataaccess.exception.DataAccessLayerException;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.masterdata.constant.DeviceRegisterErrorCode;
import io.mosip.kernel.masterdata.constant.MasterDataConstant;
import io.mosip.kernel.masterdata.constant.RegisteredDeviceErrorCode;
import io.mosip.kernel.masterdata.dto.DeviceDeRegisterResponse;
import io.mosip.kernel.masterdata.dto.getresponse.ResponseDto;
import io.mosip.kernel.masterdata.dto.registerdevice.DeviceData;
import io.mosip.kernel.masterdata.dto.registerdevice.DeviceResponse;
import io.mosip.kernel.masterdata.dto.registerdevice.DigitalId;
import io.mosip.kernel.masterdata.dto.registerdevice.HeaderRequest;
import io.mosip.kernel.masterdata.dto.registerdevice.RegisterDeviceResponse;
import io.mosip.kernel.masterdata.dto.registerdevice.RegisteredDevicePostDto;
import io.mosip.kernel.masterdata.dto.registerdevice.SignRequestDto;
import io.mosip.kernel.masterdata.dto.registerdevice.SignResponseDto;
import io.mosip.kernel.masterdata.entity.Device;
import io.mosip.kernel.masterdata.entity.DeviceProvider;
import io.mosip.kernel.masterdata.entity.RegisteredDevice;
import io.mosip.kernel.masterdata.entity.RegisteredDeviceHistory;
import io.mosip.kernel.masterdata.exception.DataNotFoundException;
import io.mosip.kernel.masterdata.exception.MasterDataServiceException;
import io.mosip.kernel.masterdata.exception.RequestException;
import io.mosip.kernel.masterdata.repository.DeviceProviderRepository;
import io.mosip.kernel.masterdata.repository.DeviceRepository;
import io.mosip.kernel.masterdata.repository.RegisteredDeviceHistoryRepository;
import io.mosip.kernel.masterdata.repository.RegisteredDeviceRepository;
import io.mosip.kernel.masterdata.repository.RegistrationDeviceSubTypeRepository;
import io.mosip.kernel.masterdata.repository.RegistrationDeviceTypeRepository;
import io.mosip.kernel.masterdata.service.RegisteredDeviceService;
import io.mosip.kernel.masterdata.utils.ExceptionUtils;
import io.mosip.kernel.masterdata.utils.MapperUtils;
import io.mosip.kernel.masterdata.utils.MetaDataUtils;
import io.mosip.kernel.masterdata.validator.registereddevice.RegisteredDeviceConstant;

/**
 * Service Class for Create RegisteredDevice
 * 
 * @author Megha Tanga
 *
 */

@Service
public class RegisteredDeviceServiceImpl implements RegisteredDeviceService {

	@Autowired
	RegisteredDeviceRepository registeredDeviceRepository;

	@Autowired
	RegisteredDeviceHistoryRepository registeredDeviceHistoryRepo;

	@Autowired
	DeviceProviderRepository deviceProviderRepository;

	@Autowired
	RegistrationDeviceTypeRepository registrationDeviceTypeRepository;

	@Autowired
	RegistrationDeviceSubTypeRepository registrationDeviceSubTypeRepository;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	DeviceRepository deviceRepository;

	/** The registered. */
	private static String REGISTERED = "Registered";

	/** The revoked. */
	private static String REVOKED = "Revoked";

	/** The retired. */
	private static String RETIRED = "Retired";

	@Autowired
	private RestTemplate restTemplate;

	@Value("${mosip.kernel.sign-url}")
	private String signUrl;

	@Value("${spring.profiles.active}")
	private String activeProfile;

	@Value("${masterdata.registerdevice.timestamp.validate:+5}")
	private String registerDeviceTimeStamp;

	/* (non-Javadoc)
	 * @see io.mosip.kernel.masterdata.service.RegisteredDeviceService#signedRegisteredDevice(io.mosip.kernel.masterdata.dto.registerdevice.RegisteredDevicePostDto)
	 */
	@Override
	public String signedRegisteredDevice(RegisteredDevicePostDto registeredDevicePostDto) {
		RegisteredDevice entity = null;
		RegisteredDevice mapEntity = null;
		RegisteredDevice crtRegisteredDevice = null;
		String digitalIdJson;
		DeviceResponse response = new DeviceResponse();
		DeviceData deviceData = null;
		DigitalId digitalId = null;
		DeviceProvider deviceProvider = null;
		RegisterDeviceResponse registerDeviceResponse = new RegisterDeviceResponse();
		String deviceDataPayLoad = getPayLoad(registeredDevicePostDto.getDeviceData());
		String headerString, signedResponse, registerDevice = null;
		try {
			deviceData = mapper.readValue(CryptoUtil.decodeBase64(deviceDataPayLoad), DeviceData.class);
			validate(deviceData);
			digitalId = mapper.readValue(CryptoUtil.decodeBase64(deviceData.getDeviceInfo().getDigitalId()),
					DigitalId.class);
			deviceProvider = deviceProviderRepository.findByIdAndNameAndIsDeletedFalseorIsDeletedIsNullAndIsActiveTrue(
					digitalId.getDeviceProviderId(), digitalId.getDeviceProvider());
			if (deviceProvider == null) {
				throw new RequestException(RegisteredDeviceErrorCode.DEVICE_PROVIDER_NOT_EXIST.getErrorCode(),
						RegisteredDeviceErrorCode.DEVICE_PROVIDER_NOT_EXIST.getErrorMessage());
			}

			if (registrationDeviceTypeRepository
					.findByCodeAndIsDeletedFalseorIsDeletedIsNullAndIsActiveTrue(digitalId.getType()) == null) {
				throw new RequestException(RegisteredDeviceErrorCode.DEVICE_TYPE_NOT_EXIST.getErrorCode(),
						String.format(RegisteredDeviceErrorCode.DEVICE_TYPE_NOT_EXIST.getErrorMessage(),
								digitalId.getType()));
			}

			if (registrationDeviceSubTypeRepository
					.findByCodeAndIsDeletedFalseorIsDeletedIsNullAndIsActiveTrue(digitalId.getSubType()) == null) {
				throw new RequestException(RegisteredDeviceErrorCode.DEVICE_SUB_TYPE_NOT_EXIST.getErrorCode(),
						String.format(RegisteredDeviceErrorCode.DEVICE_SUB_TYPE_NOT_EXIST.getErrorMessage(),
								digitalId.getSubType()));
			}

			digitalIdJson = mapper.writeValueAsString(digitalId);
			mapEntity = MapperUtils.mapRegisteredDeviceDto(registeredDevicePostDto, digitalIdJson, deviceData,
					digitalId);
			entity = MetaDataUtils.setCreateMetaData(mapEntity, RegisteredDevice.class);
			entity.setCode(generateCodeValue(registeredDevicePostDto, deviceData, digitalId));
			entity.setIsActive(true);
			crtRegisteredDevice = registeredDeviceRepository.create(entity);

			// add new row to the history table
			RegisteredDeviceHistory entityHistory = new RegisteredDeviceHistory();
			MapperUtils.map(crtRegisteredDevice, entityHistory);
			MapperUtils.setBaseFieldValue(crtRegisteredDevice, entityHistory);
			entityHistory.setDigitalId(digitalIdJson);
			entityHistory.setEffectivetimes(crtRegisteredDevice.getCreatedDateTime());
			entityHistory.setCreatedDateTime(crtRegisteredDevice.getCreatedDateTime());
			registeredDeviceHistoryRepo.create(entityHistory);

			digitalId = mapper.readValue(digitalIdJson, DigitalId.class);

			registerDeviceResponse = MapperUtils.mapRegisteredDeviceResponse(entity);
			registerDeviceResponse
					.setDigitalId(CryptoUtil.encodeBase64(mapper.writeValueAsString(digitalId).getBytes("UTF-8")));
			registerDeviceResponse.setEnv(activeProfile);
			HeaderRequest header = new HeaderRequest();
			header.setAlg("RS256");
			header.setType("JWS");
			headerString = mapper.writeValueAsString(header);
			signedResponse = getSignedResponse(registerDeviceResponse);
			registerDevice = mapper.writeValueAsString(registerDeviceResponse);
			response.setResponse(convertToJWS(headerString, registerDevice, signedResponse));

		} catch (DataAccessLayerException | DataAccessException | IOException ex) {
			throw new MasterDataServiceException(
					RegisteredDeviceErrorCode.REGISTERED_DEVICE_INSERTION_EXCEPTION.getErrorCode(),
					RegisteredDeviceErrorCode.REGISTERED_DEVICE_INSERTION_EXCEPTION.getErrorMessage() + " "
							+ ExceptionUtils.parseException(ex));
		}

		return response.getResponse();
	}

	private void validate(DeviceData deviceData) {
		if (deviceData.getDeviceInfo() != null) {
			LocalDateTime timeStamp = deviceData.getDeviceInfo().getTimeStamp();
			String prefix = registerDeviceTimeStamp.substring(0, 1);
			String timeString = registerDeviceTimeStamp.replaceAll("\\" + prefix, "");
			if (prefix.equals("+")) {
				if (timeStamp.isAfter(timeStamp.plusMinutes(TimeUnit.MINUTES.toMillis(Long.valueOf(timeString))))) {
					throw new MasterDataServiceException("MSD-RDS-001", "Time Stamp is not proper");
				}
			} else if (prefix.equals("-")) {
				if (timeStamp.isBefore(timeStamp.plusMinutes(TimeUnit.MINUTES.toMillis(Long.valueOf(timeString))))) {
					throw new MasterDataServiceException("MSD-RDS-001", "Time Stamp is not proper");
				}
			}

		}

	}

	private String getPayLoad(String jws) {
		String[] split = jws.split("\\.");
		if (split.length >= 2) {
			return split[1];
		}
		return jws;
	}

	private String convertToJWS(String headerString, String registerDevice, String signedResponse) {

		return CryptoUtil.encodeBase64String(headerString.getBytes()) + "."
				+ CryptoUtil.encodeBase64String(registerDevice.getBytes()) + "."
				+ CryptoUtil.encodeBase64String(signedResponse.getBytes());
	}

	private String getSignedResponse(RegisterDeviceResponse registerDeviceResponse) {
		RequestWrapper<SignRequestDto> request = new RequestWrapper<>();
		SignRequestDto signatureRequestDto = new SignRequestDto();
		SignResponseDto signResponse = new SignResponseDto();
		HttpEntity<RequestWrapper<SignRequestDto>> httpEntity = new HttpEntity<>(request, new HttpHeaders());
		try {
			signatureRequestDto
					.setData(CryptoUtil.encodeBase64String(mapper.writeValueAsBytes(registerDeviceResponse)));
			request.setRequest(signatureRequestDto);
			ResponseEntity<String> response = restTemplate.exchange(signUrl, HttpMethod.POST, httpEntity, String.class);
			ResponseWrapper<?> responseObject;
			try {
				responseObject = mapper.readValue(response.getBody(), ResponseWrapper.class);
				signResponse = mapper.readValue(mapper.writeValueAsString(responseObject.getResponse()),
						SignResponseDto.class);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return signResponse.getSignature();
	}

	private String generateCodeValue(RegisteredDevicePostDto registeredDevicePostDto, DeviceData deviceData,
			DigitalId digitalId) {
		String code = "";
		if (deviceData.getPurpose().equalsIgnoreCase(RegisteredDeviceConstant.REGISTRATION)) {
			List<Device> device = deviceRepository
					.findDeviceBySerialNumberAndIsDeletedFalseorIsDeletedIsNullNoIsActive(digitalId.getSerialNo());
			if (device.isEmpty()) {
				throw new RequestException(RegisteredDeviceErrorCode.SERIALNUM_NOT_EXIST.getErrorCode(), String.format(
						RegisteredDeviceErrorCode.SERIALNUM_NOT_EXIST.getErrorMessage(), digitalId.getSerialNo()));
			}
			// copy Device id as code
			code = device.get(0).getId();
		} else if (deviceData.getPurpose().equalsIgnoreCase(RegisteredDeviceConstant.AUTH)) {
			// should be uniquely randomly generated
			code = UUID.randomUUID().toString();
		}
		return code;
	}

	@Override
	public DeviceDeRegisterResponse deRegisterDevice(@Valid String deviceCode) {
		RegisteredDevice deviceRegisterEntity = null;
		RegisteredDeviceHistory deviceRegisterHistory = new RegisteredDeviceHistory();
		try {
			deviceRegisterEntity = registeredDeviceRepository.findByCodeAndIsActiveIsTrue(deviceCode);
			if (deviceRegisterEntity != null) {
				if (Arrays.asList(REVOKED, RETIRED).contains(deviceRegisterEntity.getStatusCode())) {
					throw new MasterDataServiceException(
							DeviceRegisterErrorCode.DEVICE_DE_REGISTERED_ALREADY.getErrorCode(),
							DeviceRegisterErrorCode.DEVICE_DE_REGISTERED_ALREADY.getErrorMessage());
				}
				deviceRegisterEntity.setStatusCode("Retired");
				deviceRegisterEntity = MetaDataUtils.setUpdateMetaData(deviceRegisterEntity, deviceRegisterEntity,
						false);
				registeredDeviceRepository.update(deviceRegisterEntity);
				MapperUtils.map(deviceRegisterEntity, deviceRegisterHistory);
				deviceRegisterHistory.setEffectivetimes(deviceRegisterEntity.getUpdatedDateTime());
				registeredDeviceHistoryRepo.create(deviceRegisterHistory);
			} else {

				throw new DataNotFoundException(
						DeviceRegisterErrorCode.DEVICE_REGISTER_NOT_FOUND_EXCEPTION.getErrorCode(),
						DeviceRegisterErrorCode.DEVICE_REGISTER_NOT_FOUND_EXCEPTION.getErrorMessage());
			}

		} catch (DataAccessLayerException | DataAccessException e) {
			throw new MasterDataServiceException(
					DeviceRegisterErrorCode.DEVICE_REGISTER_DELETED_EXCEPTION.getErrorCode(),
					DeviceRegisterErrorCode.DEVICE_REGISTER_DELETED_EXCEPTION.getErrorMessage() + " "
							+ ExceptionUtils.parseException(e));
		}
		DeviceDeRegisterResponse response = new DeviceDeRegisterResponse();
		response.setStatus("success");
		response.setMessage("Device deregistered successfully");
		return response;
	}

	@Transactional
	@Override
	public ResponseDto updateStatus(String deviceCode, String statusCode) {
		RegisteredDevice deviceRegister = null;
		try {
			deviceRegister = registeredDeviceRepository.findByCodeAndIsActiveIsTrue(deviceCode);
		} catch (DataAccessException | DataAccessLayerException e) {
			throw new MasterDataServiceException(DeviceRegisterErrorCode.DEVICE_REGISTER_FETCH_EXCEPTION.getErrorCode(),
					DeviceRegisterErrorCode.DEVICE_REGISTER_FETCH_EXCEPTION.getErrorMessage() + " "
							+ ExceptionUtils.parseException(e));
		}

		if (deviceRegister == null) {
			throw new DataNotFoundException(DeviceRegisterErrorCode.DATA_NOT_FOUND_EXCEPTION.getErrorCode(),
					DeviceRegisterErrorCode.DATA_NOT_FOUND_EXCEPTION.getErrorMessage());
		}
		if (deviceRegister.getStatusCode().equalsIgnoreCase(statusCode)) {
			throw new MasterDataServiceException(
					DeviceRegisterErrorCode.DEVICE_REGISTERED_STATUS_ALREADY.getErrorCode(), String.format(
							DeviceRegisterErrorCode.DEVICE_REGISTERED_STATUS_ALREADY.getErrorMessage(), statusCode));

		}

		if (!Arrays.asList(REGISTERED, REVOKED, RETIRED).contains(statusCode)) {
			throw new RequestException(DeviceRegisterErrorCode.INVALID_STATUS_CODE.getErrorCode(),
					DeviceRegisterErrorCode.INVALID_STATUS_CODE.getErrorMessage());
		}
		deviceRegister.setStatusCode(statusCode);
		updateRegisterDetails(deviceRegister);
		createHistoryDetails(deviceRegister);
		ResponseDto responseDto = new ResponseDto();
		responseDto.setMessage(MasterDataConstant.DEVICE_REGISTER_UPDATE_MESSAGE);
		responseDto.setStatus(MasterDataConstant.SUCCESS);
		return responseDto;
	}

	/**
	 * Creates the history details.
	 *
	 * @param deviceRegister
	 *            the device register
	 */
	private void createHistoryDetails(RegisteredDevice deviceRegister) {
		RegisteredDeviceHistory deviceRegisterHistory = new RegisteredDeviceHistory();
		MapperUtils.map(deviceRegister, deviceRegisterHistory);
		deviceRegisterHistory.setEffectivetimes(LocalDateTime.now(ZoneId.of("UTC")));
		try {
			registeredDeviceHistoryRepo.create(deviceRegisterHistory);
		} catch (DataAccessException | DataAccessLayerException e) {
			throw new MasterDataServiceException(
					DeviceRegisterErrorCode.DEVICE_REGISTER_UPDATE_EXCEPTION.getErrorCode(),
					DeviceRegisterErrorCode.DEVICE_REGISTER_UPDATE_EXCEPTION.getErrorMessage() + " "
							+ ExceptionUtils.parseException(e));
		}

	}

	/**
	 * Update register details.
	 *
	 * @param deviceRegister
	 *            the device register
	 */
	private void updateRegisterDetails(RegisteredDevice deviceRegister) {
		try {
			deviceRegister = MetaDataUtils.setUpdateMetaData(deviceRegister, deviceRegister, false);
			registeredDeviceRepository.update(deviceRegister);
		} catch (DataAccessException | DataAccessLayerException e) {
			throw new MasterDataServiceException(
					DeviceRegisterErrorCode.DEVICE_REGISTER_UPDATE_EXCEPTION.getErrorCode(),
					DeviceRegisterErrorCode.DEVICE_REGISTER_UPDATE_EXCEPTION.getErrorMessage() + " "
							+ ExceptionUtils.parseException(e));
		}

	}
}
