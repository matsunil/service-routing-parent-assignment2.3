package com.thoughtmechanix.assets.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.thoughtmechanix.assets.clients.OrganizationRestTemplateClient;
import com.thoughtmechanix.assets.config.ServiceConfig;
import com.thoughtmechanix.assets.exception.ResourceNotFoundException;
import com.thoughtmechanix.assets.model.Asset;
import com.thoughtmechanix.assets.model.Organization;
import com.thoughtmechanix.assets.repository.AssetRepository;

@Service
public class AssetService {

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	ServiceConfig config;

	@Autowired
	OrganizationRestTemplateClient organizationRestClient;

	private static final Logger logger = LoggerFactory.getLogger(AssetService.class);

	@HystrixCommand
	public Asset getAsset(String organizationId,String assetId) throws InterruptedException {
		Asset asset = assetRepository.findByOrganizationIdAndAssetId(organizationId, assetId);

		Organization org = getOrganization(organizationId);

		return asset
				.withOrganizationName( org.getName())
				.withContactName( org.getContactName())
				.withContactEmail( org.getContactEmail() )
				.withContactPhone( org.getContactPhone() )
				.withComment(config.getExampleProperty());
	}

	@HystrixCommand
	private Organization getOrganization(String organizationId) {
		return organizationRestClient.getOrganization(organizationId);
	}

	private void randomlyRunLong(){
		Random rand = new Random();

		int randomNum = rand.nextInt((3 - 1) + 1) + 1;

		if (randomNum==3) sleep();
	}

	private void sleep(){
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	@HystrixCommand(fallbackMethod = "buildFallbackAssetList",
			threadPoolKey = "assetByOrgThreadPool",
			threadPoolProperties =
		{@HystrixProperty(name = "coreSize",value="30"),
				@HystrixProperty(name="maxQueueSize", value="10"),
		},
		commandProperties={        
				@HystrixProperty(name="circuitBreaker.requestVolumeThreshold", value="10"),
				@HystrixProperty(name="circuitBreaker.errorThresholdPercentage", value="75"),
				@HystrixProperty(name="circuitBreaker.sleepWindowInMilliseconds", value="7000"),
				@HystrixProperty(name="metrics.rollingStats.timeInMilliseconds", value="15000"),
				@HystrixProperty(name="metrics.rollingStats.numBuckets", value="5")}
			)
	public List<Asset> getAssetsByOrg(String organizationId){
		randomlyRunLong();

		return assetRepository.findByOrganizationId(organizationId);
	}

	private List<Asset> buildFallbackAssetList(String organizationId){
		List<Asset> fallbackList = new ArrayList<>();
		Asset asset = new Asset()
				.withId("0000000-00-00000")
				.withOrganizationId( organizationId )
				.withAssetName("Sorry no assets information currently available");

		fallbackList.add(asset);
		return fallbackList;
	}

	public void saveAsset(String organizationId, Asset asset){
		asset.withId( UUID.randomUUID().toString());
		asset.setOrganizationId(organizationId);

		assetRepository.save(asset);
	}

	public Asset updateAsset(String organizationId, String assetId, Asset assetRequest) throws ResourceNotFoundException {
		Asset asset = assetRepository.findByOrganizationIdAndAssetId(organizationId, assetId);
		if (asset != null) {
			asset.setAssetName(assetRequest.getAssetName());
			asset.setAssetType(assetRequest.getAssetType());
			asset.setComment(assetRequest.getComment());

			return assetRepository.save(asset);
		}

		throw new ResourceNotFoundException("No asset found with organizationId="+organizationId+" and assetId="+assetId);
	}

	public void deleteAsset(String organizationId, String assetId) throws ResourceNotFoundException {
		Asset asset = assetRepository.findByOrganizationIdAndAssetId(organizationId, assetId);
		if (asset != null) {
			assetRepository.delete( asset.getAssetId());
		} else {
			throw new ResourceNotFoundException("No asset found with organizationId="+organizationId+" and assetId="+assetId);
		}
	}

}
