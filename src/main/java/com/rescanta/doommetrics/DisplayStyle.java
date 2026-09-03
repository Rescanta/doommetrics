package com.rescanta.doommetrics;

public enum DisplayStyle
{
	PANEL("Panel"),
	INFOBOX("Infobox"),
	OFF("Off");

	private final String label;

	DisplayStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
