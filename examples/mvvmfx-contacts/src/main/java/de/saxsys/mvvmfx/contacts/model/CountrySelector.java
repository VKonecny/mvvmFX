package de.saxsys.mvvmfx.contacts.model;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.datafx.provider.ListDataProvider;
import org.datafx.reader.FileSource;
import org.datafx.reader.converter.XmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CountrySelector {

	private static final Logger LOG = LoggerFactory.getLogger(CountrySelector.class);
	
	public static final String ISO_3166_LOCATION = "/countries/iso_3166.xml";
	public static final String ISO_3166_2_LOCATION = "/countries/iso_3166_2.xml";
	private ObservableList<Country> countries = FXCollections.observableArrayList();
	private ObservableList<Subdivision> subdivisions = FXCollections.observableArrayList();
	
	
	private ReadOnlyStringWrapper subdivisionLabel = new ReadOnlyStringWrapper();
	
	private ReadOnlyBooleanWrapper inProgress = new ReadOnlyBooleanWrapper(false);
	
	private Map<Country, List<Subdivision>> countryCodeSubdivisionMap = new HashMap<>();
	private Map<Country, String> countryCodeSubdivisionNameMap = new HashMap<>();


	public void init(){
		inProgress.set(true);
		loadCountries();
	}

	public void setCountry(Country country){
		if(country == null){
			subdivisionLabel.set(null);
			subdivisions.clear();
			return;
		}

		subdivisionLabel.set(countryCodeSubdivisionNameMap.get(country));

		subdivisions.clear();
		if(countryCodeSubdivisionMap.containsKey(country)){
			subdivisions.addAll(countryCodeSubdivisionMap.get(country));
		}
	}
	
	void loadCountries(){
		URL iso3166Resource = this.getClass().getResource(ISO_3166_LOCATION);
		if(iso3166Resource == null){
			throw new IllegalStateException("Can't find the list of countries! Expected location was:" + ISO_3166_LOCATION);
		}

		XmlConverter<Country> countryConverter = new XmlConverter<>("iso_3166_entry",Country.class);

		try {
			FileSource<Country> dataSource = new FileSource<>(new File(iso3166Resource.getFile()),countryConverter);
			ListDataProvider<Country> listDataProvider = new ListDataProvider<>(dataSource);

			listDataProvider.setResultObservableList(countries);

			Worker<ObservableList<Country>> worker = listDataProvider.retrieve();
			worker.stateProperty().addListener(obs -> {
				if (worker.getState() == Worker.State.SUCCEEDED) {
					loadSubdivisions();
				}
			});
		} catch (IOException e) {
			LOG.error("A problem was detected while loading the XML file with the available countries.", e);
		}
	}
	
	void loadSubdivisions(){

		URL iso3166_2Resource = this.getClass().getResource(ISO_3166_2_LOCATION);
		
		if(iso3166_2Resource == null){
			throw new IllegalStateException("Can't find the list of subdivisions! Expected location was:" + 
					ISO_3166_2_LOCATION);
		}
		
		XmlConverter<ISO3166_2_CountryEntity> converter = new XmlConverter<>("iso_3166_country", ISO3166_2_CountryEntity.class);

		ObservableList<ISO3166_2_CountryEntity> subdivisionsEntities = FXCollections.observableArrayList();

		try{
			FileSource<ISO3166_2_CountryEntity> dataSource = new FileSource<>(new File(iso3166_2Resource.getFile()), converter);
			ListDataProvider<ISO3166_2_CountryEntity> listDataProvider = new ListDataProvider<>(dataSource);

			listDataProvider.setResultObservableList(subdivisionsEntities);

			Worker<ObservableList<ISO3166_2_CountryEntity>> worker = listDataProvider.retrieve();
			worker.stateProperty().addListener(obs -> {
				if(worker.getState() == Worker.State.SUCCEEDED){
					
					subdivisionsEntities.forEach(entity->{
						if(entity.subsets != null && !entity.subsets.isEmpty()){

							Country country = findCountryByCode(entity.code);

							if(!countryCodeSubdivisionMap.containsKey(country)){
								countryCodeSubdivisionMap.put(country, new ArrayList<>());
							}
							
							List<Subdivision> subdivisionList = countryCodeSubdivisionMap.get(country);
	
							entity.subsets.get(0).entryList.forEach(entry -> {
								subdivisionList.add(new Subdivision(entry.name, entry.code, country));
							});
							
							countryCodeSubdivisionNameMap.put(country, entity.subsets.get(0).subdivisionType);
						}
					});

					inProgress.set(false);
				}
			});
		}catch (IOException e){
			LOG.error("A problem was detected while loading the XML file with the available subdivisions.", e);
		}
		
	}
	
	private Country findCountryByCode(String code){
		return countries.stream().filter(country-> country.getCountryCode().equals(code)).findFirst().orElse(null);
	}
	
	
	

	@XmlRootElement(name = "iso_3166_subset")
	@XmlAccessorType(XmlAccessType.FIELD)
	static class ISO3166_2_EntryEntity{
		@XmlAttribute(name="code")
		public String code;
		@XmlAttribute(name="name")
		public String name;
	}


	@XmlRootElement(name = "iso_3166_subset")
	@XmlAccessorType(XmlAccessType.FIELD)
	static class ISO3166_2_SubsetEntity{
		@XmlElement(name = "iso_3166_2_entry")
		public List<ISO3166_2_EntryEntity> entryList;
		
		@XmlAttribute(name="type")
		public String subdivisionType;
	}

	@XmlRootElement(name = "iso_3166_country")
	@XmlAccessorType(XmlAccessType.FIELD)
	static class ISO3166_2_CountryEntity{
		@XmlAttribute(name="code")
		public String code;
		
		@XmlElement(name="iso_3166_subset")
		public List<ISO3166_2_SubsetEntity> subsets;

		@Override
		public String toString() {
			return "CountryEntity " + code;
		}
	}
	
	public ObservableList<Country> availableCountries(){
		return countries;
	}
	
	
	
	public ReadOnlyStringProperty subdivisionLabel(){
		return subdivisionLabel.getReadOnlyProperty();
	}
	
	public ObservableList<Subdivision> subdivisions(){
		return FXCollections.unmodifiableObservableList(subdivisions);
	}

	public ReadOnlyBooleanProperty inProgressProperty(){
		return inProgress.getReadOnlyProperty();
	}
}