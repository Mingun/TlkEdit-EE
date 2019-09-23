/*
 * Created on 13.03.2005
 */
package org.jl.swing;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import javax.swing.Icon;

/**
 */
public class ScaledIcon implements Icon {
	private final int x;
	private final int y;
	private final double scaleX;
	private final double scaleY;
	private final Icon icon;

	public ScaledIcon( Icon icon, int x, int y ){
		this.icon=icon;
		this.x=x;
		this.y=y;
		scaleX = ((double)x)/((double)icon.getIconWidth());
		scaleY = ((double)y)/((double)icon.getIconHeight());
	}

	/* (non-Javadoc)
	 * @see javax.swing.Icon#paintIcon(java.awt.Component, java.awt.Graphics, int, int)
	 */
	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g;
		AffineTransform t = g2.getTransform();
		g2.translate(x,y);
		g2.scale(scaleX, scaleY);
		icon.paintIcon( c,g2,0,0 );
		g2.setTransform(t);
	}

	/* (non-Javadoc)
	 * @see javax.swing.Icon#getIconWidth()
	 */
	@Override
	public int getIconWidth() {
		return x;
	}

	/* (non-Javadoc)
	 * @see javax.swing.Icon#getIconHeight()
	 */
	@Override
	public int getIconHeight() {
		return y;
	}
}
