package edu.shu.dao;

import java.util.List;

import edu.shu.entity.DeviceLGstatus;


public interface DeviceLGstatusDAO {

	//插入一条设备信息
		public void insertLGDevice(DeviceLGstatus condition);
		//查询一条设备的IMEI码
		public DeviceLGstatus searchLGDevice(String IMEI);
		//获取所有在使用的IMEI
		public List<String> GetAllDevice();
}
