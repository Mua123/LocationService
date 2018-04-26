package edu.shu.entity;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * DeviceStatus entity. @author MyEclipse Persistence Tools
 */
@Entity
@Table(name = "device_status_new")
public class DeviceStatusRecent implements java.io.Serializable {

	// Fields

	@Id
	@GeneratedValue
	@Column(name = "ID", unique = true, nullable = false)
	private Integer id;
	@Column(name = "IMEI", length = 15)
	private String imei;
	@Column(name = "DEVICE_TYPE", length = 10)
	private String device_type;
	@Column(name = "BATTERY", precision = 12, scale = 0)
	private Float battery;
	@Column(name = "GSM")
	private Integer gsm;
	@Column(name = "OILELECTIRC")
	private Boolean oilelectirc;
	@Column(name = "GPSSTATE")
	private Boolean gpsstate;
	@Column(name = "Charging")
	private Boolean charging;
	@Column(name = "ACC")
	private Boolean acc;
	@Column(name = "GUARD")
	private Boolean guard;
	@Column(name = "LANGUAGE")
	private Boolean language;
	@Column(name = "DATE_ADD", length = 0)
	private Timestamp date_add;
	
	// Constructors


	public Integer getId() {
		return this.id;
	}

	public DeviceStatusRecent() {
		
	}
	
	public DeviceStatusRecent(Integer id, String imei, String device_type, Float battery, Integer gsm, Boolean oilelectirc,
			Boolean gpsstate, Boolean charging, Boolean acc, Boolean guard, Boolean language, Timestamp date_add) {
		super();
		this.id = id;
		this.imei = imei;
		this.device_type = device_type;
		this.battery = battery;
		this.gsm = gsm;
		this.oilelectirc = oilelectirc;
		this.gpsstate = gpsstate;
		this.charging = charging;
		this.acc = acc;
		this.guard = guard;
		this.language = language;
		this.date_add = date_add;
	}

	public void setId(Integer id) {
		this.id = id;
	}


	public String getImei() {
		return this.imei;
	}

	public void setImei(String imei) {
		this.imei = imei;
	}


	public Float getBattery() {
		return this.battery;
	}

	public void setBattery(Float battery) {
		this.battery = battery;
	}


	public Integer getGsm() {
		return this.gsm;
	}

	public void setGsm(Integer gsm) {
		this.gsm = gsm;
	}


	public Boolean getOilelectirc() {
		return this.oilelectirc;
	}

	public void setOilelectirc(Boolean oilelectirc) {
		this.oilelectirc = oilelectirc;
	}


	public Boolean getGpsstate() {
		return this.gpsstate;
	}

	public void setGpsstate(Boolean gpsstate) {
		this.gpsstate = gpsstate;
	}


	public Boolean getCharging() {
		return this.charging;
	}

	public void setCharging(Boolean charging) {
		this.charging = charging;
	}


	public Boolean getAcc() {
		return this.acc;
	}

	public void setAcc(Boolean acc) {
		this.acc = acc;
	}


	public Boolean getGuard() {
		return this.guard;
	}

	public void setGuard(Boolean guard) {
		this.guard = guard;
	}


	public Boolean getLanguage() {
		return this.language;
	}

	public void setLanguage(Boolean language) {
		this.language = language;
	}

	public Timestamp getDate_add() {
		return date_add;
	}

	public void setDate_add(Timestamp date_add) {
		this.date_add = date_add;
	}

	public String getDevice_type() {
		return device_type;
	}

	public void setDevice_type(String device_type) {
		this.device_type = device_type;
	}
	
}