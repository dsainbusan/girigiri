package net.dsa.girigiri.domain.dto;

import lombok.Getter;
import lombok.Setter;
import net.dsa.girigiri.domain.entity.StoreEntity;

/**
 * 사장님용 매장 정보(카테고리/위치/영업시간 등) 등록·수정 화면 입력값.
 * StoreEntity를 그대로 노출하지 않기 위한 폼 전용 DTO.
 */
@Getter
@Setter
public class StoreSettingsForm {

	private String storeName;
	private String category;
	private String address;
	private Double latitude;
	private Double longitude;
	private String operatingHours;
	private String phone;

	public static StoreSettingsForm from(StoreEntity store) {
		StoreSettingsForm form = new StoreSettingsForm();
		form.storeName = store.getStoreName();
		form.category = store.getCategory();
		form.address = store.getAddress();
		form.latitude = store.getLatitude();
		form.longitude = store.getLongitude();
		form.operatingHours = store.getOperatingHours();
		form.phone = store.getPhone();
		return form;
	}
}
