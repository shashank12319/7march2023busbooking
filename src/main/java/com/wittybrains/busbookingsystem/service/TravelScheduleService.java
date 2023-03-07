
package com.wittybrains.busbookingsystem.service;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.wittybrains.busbookingsystem.controller.TravelScheduleResponseWrapper;
import com.wittybrains.busbookingsystem.dto.StationDTO;
import com.wittybrains.busbookingsystem.dto.TravelScheduleDTO;
import com.wittybrains.busbookingsystem.exception.UnprocessableEntityException;
import com.wittybrains.busbookingsystem.model.Station;
import com.wittybrains.busbookingsystem.model.TravelSchedule;
import com.wittybrains.busbookingsystem.repository.StationRepository;
import com.wittybrains.busbookingsystem.repository.TravelScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TravelScheduleService {
	private static final Logger logger = LoggerFactory.getLogger(TravelScheduleService.class);
	private static final int MAX_SEARCH_DAYS = 30;
	private final TravelScheduleRepository scheduleRepository;
	private final StationRepository stationRepository;

	public TravelScheduleService(TravelScheduleRepository scheduleRepository, StationRepository stationRepository) {
		this.scheduleRepository = scheduleRepository;
		this.stationRepository = stationRepository;
	}

	public Station getStationByCode(String code) {
		Optional<Station> optionalStation = stationRepository.findByStationCode(code);
		return optionalStation.orElse(null);
	}

	public ResponseEntity<TravelScheduleResponseWrapper> getAvailableSchedule(String sourceCode, String destinationCode,
			String date) {

		// Check if date is empty or null
		if (StringUtils.isEmpty(date)) {
			String message = "Date is null or empty. ";
			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}
		// Check if destinationCode is empty or null
		if (StringUtils.isEmpty(destinationCode)) {
			String message = "Destination station code is null or empty. ";
			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

		// Check if sourceCode is empty or null
		if (StringUtils.isEmpty(sourceCode)) {
			String message = "Source station code is null or empty.";
			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

		// Check if source and destination stations exist in the database
		Station source = getStationByCode(sourceCode);
		if (source == null) {
			String message = "Invalid source with station code " + sourceCode;
			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
		}
		Station destination = getStationByCode(destinationCode);
		if (destination == null) {
			String message = "Invalid destination with station code " + destinationCode;
			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
		}

		// Check if source and destination stations are the same
		if (sourceCode.equals(destinationCode)) {
			String message = "Source and destination station codes cannot be the same. ";
			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

		try {

			LocalDate parsedDate = LocalDate.parse(date);
			List<TravelScheduleDTO> schedules = getAvailableSchedules(source, destination, parsedDate);

			if (schedules.isEmpty()) {
				String message = "No schedule is available for the date you searched for.";
				TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message, schedules);
				logger.info(message);
				return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
			} else {
				String message = "Available schedules between " + source.getName() + " and " + destination.getName()
						+ " on " + date.toString();
				TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message, schedules);
				logger.info(message);
				return new ResponseEntity<>(response, HttpStatus.OK);
			}

		} catch (DateTimeParseException ex) {
			String message = "Invalid date format. The correct format is ISO date format (yyyy-MM-dd) ";

			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		} catch (UnprocessableEntityException ex) {
			String message = ex.getMessage();
			TravelScheduleResponseWrapper response = new TravelScheduleResponseWrapper(message,
					Collections.emptyList());
			logger.warn(message);
			return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
		}
	}

	public List<TravelScheduleDTO> getAvailableSchedules(Station source, Station destination, LocalDate searchDate) {
		LocalDateTime currentDateTime = LocalDateTime.now();
		LocalDate currentDate = currentDateTime.toLocalDate();
		LocalTime currentTime = currentDateTime.toLocalTime();

		LocalDateTime searchDateTime = LocalDateTime.of(searchDate, LocalTime.MIDNIGHT);
		if (searchDate.isBefore(currentDate)) {
			// cannot search for past schedules
			String message = "Cannot search for schedules in the past";
			logger.warn(message);

			throw new UnprocessableEntityException(message);

		} else if (searchDate.equals(currentDate)) {
			// search for schedules at least 1 hour from now
			searchDateTime = LocalDateTime.of(searchDate, currentTime.plusHours(1));
		}

		LocalDateTime maxSearchDateTime = currentDateTime.plusDays(MAX_SEARCH_DAYS);
		if (searchDateTime.isAfter(maxSearchDateTime)) {
			// cannot search for schedules more than one month in the future
			String message = "Cannot search for schedules more than one month in the future";
			logger.warn(message);
			throw new UnprocessableEntityException(message);

		}

		List<TravelSchedule> travelScheduleList = scheduleRepository
				.findBySourceAndDestinationAndEstimatedArrivalTimeAfter(source, destination, currentDateTime);

		List<TravelScheduleDTO> travelScheduleDTOList = new ArrayList<>();
		for (TravelSchedule travelSchedule : travelScheduleList) {
			TravelScheduleDTO travelScheduleDTO = new TravelScheduleDTO(travelSchedule);
			travelScheduleDTOList.add(travelScheduleDTO);
		}

		if (travelScheduleDTOList.isEmpty()) {
			String message = "No available schedules found for the given search criteria";
			logger.warn(message);
		}

		return travelScheduleDTOList;
	}

	public boolean createSchedule(TravelScheduleDTO travelScheduleDTO) throws ParseException {
		logger.info("Creating travel schedule: {}", travelScheduleDTO);

		TravelSchedule travelschedule = new TravelSchedule();

		StationDTO destinationDTO = travelScheduleDTO.getDestination();
		Station destination = getStationByCode(destinationDTO.getStationCode());
		travelschedule.setDestination(destination);

		Station source = getStationByCode(travelScheduleDTO.getSource().getStationCode());
		travelschedule.setSource(source);

		travelschedule = scheduleRepository.save(travelschedule);

		logger.info("Created travel schedule ");
		return travelschedule.getScheduleId() != null;
	}
}
