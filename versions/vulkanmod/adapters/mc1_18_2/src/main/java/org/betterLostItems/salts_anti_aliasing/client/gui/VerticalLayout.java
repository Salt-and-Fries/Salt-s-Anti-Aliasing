package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;
import java.util.*;
import java.util.function.Consumer;
final class VerticalLayout implements Layout {
    private final List<LayoutElement> children=new ArrayList<>();
    private final int gap;private int x,y,width,height;
    VerticalLayout(int gap){this.gap=gap;}
    VerticalLayout defaultCellSetting(){return this;} VerticalLayout alignHorizontallyCenter(){return this;}
    <T> T addChild(T value){children.add(WidgetElement.of(value));return value;}
    public void arrangeElements(){
        for(var child:children)if(child instanceof Layout layout)layout.arrangeElements();
        width=children.stream().mapToInt(LayoutElement::getWidth).max().orElse(0);
        height=children.stream().mapToInt(LayoutElement::getHeight).sum()+Math.max(0,children.size()-1)*gap;
        int top=y;for(var child:children){child.setX(x+(width-child.getWidth())/2);child.setY(top);top+=child.getHeight()+gap;}
    }
    public void visitChildren(Consumer<LayoutElement> consumer){children.forEach(consumer);}
    public int getX(){return x;}public int getY(){return y;}public int getWidth(){return width;}public int getHeight(){return height;}
    public void setX(int value){int delta=value-x;x=value;for(var child:children)child.setX(child.getX()+delta);}
    public void setY(int value){int delta=value-y;y=value;for(var child:children)child.setY(child.getY()+delta);}
}
