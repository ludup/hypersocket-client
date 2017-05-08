package com.hypersocket.client.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface FavouriteItemService extends Remote{

	FavouriteItem getFavouriteItem(String uid) throws RemoteException;
	
	FavouriteItem getFavouriteItem(Long id) throws RemoteException;
	
	List<FavouriteItem> getFavouriteItems() throws RemoteException;
	
	List<FavouriteItem> getFavouriteItems(String...uids) throws RemoteException;
	
	List<FavouriteItem> getFavouriteItems(Long...ids) throws RemoteException;
	
	void saveOrUpdate(FavouriteItem favouriteItem) throws RemoteException;
	
	void delete(Long id) throws RemoteException;
	
	void delete(String uid) throws RemoteException;
}