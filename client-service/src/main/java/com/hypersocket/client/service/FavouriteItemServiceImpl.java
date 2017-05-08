package com.hypersocket.client.service;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

import com.hypersocket.client.db.HibernateSessionFactory;
import com.hypersocket.client.rmi.FavouriteItem;
import com.hypersocket.client.rmi.FavouriteItemService;

public class FavouriteItemServiceImpl implements FavouriteItemService {
	
	Session session;
	
	public FavouriteItemServiceImpl() {
		session = HibernateSessionFactory.getFactory().openSession();
	}
	
	@Override
	public FavouriteItem getFavouriteItem(String uid) {
		Criteria criteria = session.createCriteria(FavouriteItem.class);
		criteria.add(Restrictions.eq("uid", uid));
		return (FavouriteItem) criteria.uniqueResult();
	}

	@Override
	public FavouriteItem getFavouriteItem(Long id) {
		Criteria criteria = session.createCriteria(FavouriteItem.class);
		criteria.add(Restrictions.eq("id", id));
		return (FavouriteItem) criteria.uniqueResult();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<FavouriteItem> getFavouriteItems() {
		Criteria criteria = session.createCriteria(FavouriteItem.class);
		return criteria.list();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<FavouriteItem> getFavouriteItems(String... uids) {
		Criteria criteria = session.createCriteria(FavouriteItem.class, "fi");
		criteria.add(Restrictions.in("uid", uids));
		return criteria.list();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<FavouriteItem> getFavouriteItems(Long... ids) {
		Criteria criteria = session.createCriteria(FavouriteItem.class);
		criteria.add(Restrictions.in("id", ids));
		return criteria.list();
	}

	@Override
	public void saveOrUpdate(FavouriteItem favouriteItem) {
		Transaction transaction = null;
		try{
			transaction = session.beginTransaction();
			session.saveOrUpdate(favouriteItem);
			session.flush();
			transaction.commit();
		} catch (Exception e) {
			if(transaction != null) {
				transaction.rollback();
			}
			throw new IllegalStateException(e.getMessage(), e);
		} 
	}

	@Override
	public void delete(Long id) {
		Transaction transaction = null;
		try{
			transaction = session.beginTransaction();
			FavouriteItem favouriteItem = (FavouriteItem) session.load(FavouriteItem.class, id);
			session.delete(favouriteItem);
			session.flush();
			transaction.commit();
		} catch (Exception e) {
			if(transaction != null) {
				transaction.rollback();
			}
			throw new IllegalStateException(e.getMessage(), e);
		} 
	}

	@Override
	public void delete(String uid) {
		FavouriteItem favouriteItem = getFavouriteItem(uid);
		delete(favouriteItem.getId());
	}

}
