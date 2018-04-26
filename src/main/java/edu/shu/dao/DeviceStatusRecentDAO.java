package edu.shu.dao;

import java.util.List;

import edu.shu.entity.DeviceStatusRecent;



public interface DeviceStatusRecentDAO {
	//插入一条设备信息
	public void insertDevice(DeviceStatusRecent condition);
	//更新一条设备信息
	public void updateDevice(DeviceStatusRecent condition);
	//查询一条设备的IMEI码
	public DeviceStatusRecent searchDecice(String IMEI);
	//获取所有在使用的IMEI
	public List<String> GetAllDevice();
}
