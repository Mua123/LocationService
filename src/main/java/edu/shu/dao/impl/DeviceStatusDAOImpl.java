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

import edu.shu.dao.DeviceStatusDAO;
import edu.shu.entity.DeviceStatus;

@Repository
public class DeviceStatusDAOImpl implements DeviceStatusDAO{
	@Autowired()
	SessionFactory sessionfactory;

	public void setSessionfactory(SessionFactory sessionfactory) {
		this.sessionfactory = sessionfactory;
	}

	public void insertDevice(DeviceStatus condition) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		session.save(condition);
	}

	public void updateDevice(DeviceStatus condition) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		DeviceStatus device = searchDecice(condition.getImei());
		if(device==null){
			insertDevice(condition);
		}else{
			session.update(condition);			
		}
	}

	public DeviceStatus searchDecice(String IMEI) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		Criteria c = session.createCriteria(DeviceStatus.class);
		c.add(Restrictions.eq("imei", IMEI));
		List<DeviceStatus> list = c.list();
		if(list.size()>0) {
			return list.get(0);
		}else {
			return null;
		}
	}

	public List<String> GetAllDevice() {
		// TODO Auto-generated method stub
		String hql = "SELECT DISTINCT a.imei  From DeviceStatus as a";
		Session session = sessionfactory.getCurrentSession();
		Query query = session.createQuery(hql);
		return query.list();
	}
	
}
