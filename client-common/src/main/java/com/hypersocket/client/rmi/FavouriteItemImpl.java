package com.hypersocket.client.rmi;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name= "favourite_items")
public class FavouriteItemImpl implements FavouriteItem, Serializable {

	private static final long serialVersionUID = 1007856764641094257L;
	
	@Id
	@GeneratedValue(strategy=GenerationType.TABLE)
	Long id;
	
	String uid;
	
	Boolean favourite;

	public Long getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public Boolean getFavourite() {
		return favourite;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public void setFavourite(Boolean favourite) {
		this.favourite = favourite;
	}
	
}
