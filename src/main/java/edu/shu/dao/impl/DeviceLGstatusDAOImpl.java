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

import edu.shu.dao.DeviceLGstatusDAO;
import edu.shu.dao.DeviceStatusDAO;
import edu.shu.entity.DeviceLGstatus;
import edu.shu.entity.DeviceStatus;

@Repository
public class DeviceLGstatusDAOImpl  implements DeviceLGstatusDAO{
	@Autowired()
	SessionFactory sessionfactory;

	public void setSessionfactory(SessionFactory sessionfactory) {
		this.sessionfactory = sessionfactory;
	}

	public void insertLGDevice(DeviceLGstatus condition) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		session.save(condition);
	}
	public DeviceLGstatus searchLGDevice(String IMEI) {
		// TODO Auto-generated method stub
		Session session = sessionfactory.getCurrentSession();
		Criteria c = session.createCriteria(DeviceLGstatus.class);
		c.add(Restrictions.eq("imei", IMEI));
		List<DeviceLGstatus> list = c.list();
		if(list.size()>0) {
			return list.get(0);
		}else {
			return null;
		}
	}
	public List<String> GetAllDevice() {
		// TODO Auto-generated method stub
		String hql = "SELECT DISTINCT a.imei  From DeviceLGstatus as a";
		Session session = sessionfactory.getCurrentSession();
		Query query = session.createQuery(hql);
		return query.list();
	}

	

}
