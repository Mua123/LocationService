package edu.shu.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import edu.shu.dao.DeviceStatusRecentDAO;
import edu.shu.entity.DeviceStatusRecent;

@Repository
public class DeviceRecentDAOImpl implements DeviceStatusRecentDAO{
	@Autowired()
	SessionFactory sessionfactory;

	public void setSessionfactory(SessionFactory sessionfactory) {
		this.sessionfactory = sessionfactory;
	}

	public void insertDevice(DeviceStatusRecent condition) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		session.save(condition);
	}

	public void updateDevice(DeviceStatusRecent condition) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		session.clear();
		DeviceStatusRecent device = searchDecice(condition.getImei());
		if(device==null){
			insertDevice(condition);
		}else{
			device.setDevice_type(condition.getDevice_type());
			device.setBattery(condition.getBattery());
			device.setGsm(condition.getGsm());
			device.setOilelectirc(condition.getOilelectirc());
			device.setGpsstate(condition.getGpsstate());
			device.setCharging(condition.getCharging());
			device.setAcc(condition.getAcc());
			device.setGuard(condition.getGuard());
			device.setLanguage(condition.getLanguage());
			device.setDate_add(condition.getDate_add());
			session.update(device);		
		}
	}

	public DeviceStatusRecent searchDecice(String IMEI) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		Criteria c = session.createCriteria(DeviceStatusRecent.class);
		c.add(Restrictions.eq("imei", IMEI));
		List<DeviceStatusRecent> list = c.list();
		if(list.size()>0) {
			return list.get(0);
		}
		return null;
	}


	
	public List<String> GetAllDevice() {
		// TODO Auto-generated method stub
		String hql = "SELECT DISTINCT a.imei  From DeviceStatusRecent as a";
		Session session = sessionfactory.getCurrentSession();
		Query query = session.createQuery(hql);
		return query.list();
	}
	
}
