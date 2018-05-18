package edu.shu.entity;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;


@Entity
@Table(name = "device_lgstatus")
public class DeviceLGstatus implements java.io.Serializable {

	private Integer id;
	private String imei;
	private String device_type; 
	private String timezoo;
	private Timestamp date_add;
	
	
	// Constructors


		/** default constructor */
		public DeviceLGstatus() {
		}
		/** full constructor */
		public DeviceLGstatus(String imei,String timezoo) {
			this.imei = imei;
		    this.timezoo = timezoo;
		
}

		
		// Property accessors
		@Id
		@GeneratedValue
		@Column(name = "ID", unique = true, nullable = false)
		public Integer getId() {
			return this.id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		@Column(name = "IMEI", length = 15)
		public String getImei() {
			return this.imei;
		}

		public void setImei(String imei) {
			this.imei = imei;
		}
		
		@Column(name = "TIMEZOO")
		public String gettimezoo() {
				return this.timezoo;
		}
		
		public void settimezoo(String timezoo) {
			this.timezoo = timezoo;
		}
		public Timestamp getDate_add() {
			return date_add;
		}
		@Column(name = "DATE_ADD", length = 0)
		public void setDate_add(Timestamp date_add) {
			this.date_add = date_add;
		}
		@Column(name = "DEVICE_TYPE", length = 10)
		public String getDevice_type() {
			return device_type;
		}

		public void setDevice_type(String device_type) {
			this.device_type = device_type;
		}
}
