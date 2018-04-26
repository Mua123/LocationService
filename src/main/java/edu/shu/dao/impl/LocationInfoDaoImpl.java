package edu.shu.dao.impl;


import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import edu.shu.dao.LocationInfoDao;


@Repository
public class LocationInfoDaoImpl implements LocationInfoDao {

	@Autowired
	SessionFactory sessionfactoty;
	public void test() {
		// TODO Auto-generated method stub
	}

}
