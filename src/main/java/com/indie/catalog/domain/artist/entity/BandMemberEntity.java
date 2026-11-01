package com.indie.catalog.domain.artist.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "band_member")
public class BandMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long artistId;

    private String name;

    private String role;

    protected BandMemberEntity() {}

    public BandMemberEntity(Long artistId, String name, String role) {
        this.artistId = artistId;
        this.name = name;
        this.role = role;
    }

    public String getName() { return name; }
    public String getRole() { return role; }
}
