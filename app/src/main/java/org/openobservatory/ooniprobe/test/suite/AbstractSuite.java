package org.openobservatory.ooniprobe.test.suite;

import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;
import android.support.annotation.XmlRes;

import org.openobservatory.ooniprobe.activity.AbstractActivity;
import org.openobservatory.ooniprobe.test.test.AbstractTest;

import java.io.Serializable;

public abstract class AbstractSuite implements Serializable {
	private int title;
	private int cardDesc;
	private int icon;
	private int color;
	private int themeLight;
	private int themeDark;
	private int desc1;
	private int desc2;
	private int pref;
	private String anim;
	private String name;

	public AbstractSuite(String name, @StringRes int title, @StringRes int cardDesc, @DrawableRes int icon, @ColorRes int color, @StyleRes int themeLight, @StyleRes int themeDark, @StringRes int desc1, @StringRes int desc2, @XmlRes int pref, String anim) {
		this.title = title;
		this.cardDesc = cardDesc;
		this.icon = icon;
		this.color = color;
		this.themeLight = themeLight;
		this.themeDark = themeDark;
		this.desc1 = desc1;
		this.desc2 = desc2;
		this.pref = pref;
		this.anim = anim;
		this.name = name;
	}

	public abstract AbstractTest[] getTestList(AbstractActivity activity);

	public int getTitle() {
		return title;
	}

	public int getCardDesc() {
		return cardDesc;
	}

	public int getIcon() {
		return icon;
	}

	public String getAnim() {
		return anim;
	}

	public int getColor() {
		return color;
	}

	public int getThemeLight() {
		return themeLight;
	}

	public int getThemeDark() {
		return themeDark;
	}

	public int getDesc1() {
		return desc1;
	}

	public int getDesc2() {
		return desc2;
	}

	public int getPref() {
		return pref;
	}

	public String getName() {
		return name;
	}
}
