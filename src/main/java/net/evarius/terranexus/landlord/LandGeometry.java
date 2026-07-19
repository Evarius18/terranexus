package net.evarius.terranexus.landlord;

import net.minecraft.util.math.BlockPos;
import java.util.List;

public final class LandGeometry {
    private LandGeometry() {}
    public static String validatePolygon(List<BlockPos> points){if(points.size()<3)return "Mindestens drei Eckpunkte sind erforderlich.";if(points.size()>256)return "Maximal 256 Eckpunkte sind zulässig.";if(points.stream().map(p->p.getX()+","+p.getZ()).distinct().count()!=points.size())return "Ein Eckpunkt wurde doppelt gesetzt.";long area=0;for(int i=0;i<points.size();i++){BlockPos a=points.get(i),b=points.get((i+1)%points.size());area+=(long)a.getX()*b.getZ()-(long)b.getX()*a.getZ();}if(area==0)return "Die Eckpunkte bilden keine Fläche.";for(int i=0;i<points.size();i++){BlockPos a=points.get(i),b=points.get((i+1)%points.size());for(int j=i+1;j<points.size();j++){if(j==i||j==(i+1)%points.size()||i==(j+1)%points.size())continue;BlockPos c=points.get(j),d=points.get((j+1)%points.size());if(intersects(a,b,c,d))return "Die Grundstückskanten kreuzen sich. Verschiebe oder entferne einen Eckpunkt.";}}return null;}
    public static String validateCuboid(BlockPos a,BlockPos b){if(a.getX()==b.getX()||a.getZ()==b.getZ())return "Ein Grundstück benötigt Breite und Länge; die Punkte dürfen nicht auf derselben Achse liegen.";return null;}
    private static boolean intersects(BlockPos a,BlockPos b,BlockPos c,BlockPos d){long o1=orientation(a,b,c),o2=orientation(a,b,d),o3=orientation(c,d,a),o4=orientation(c,d,b);return ((o1>0&&o2<0)||(o1<0&&o2>0))&&((o3>0&&o4<0)||(o3<0&&o4>0));}
    private static long orientation(BlockPos a,BlockPos b,BlockPos c){return (long)(b.getZ()-a.getZ())*(c.getX()-b.getX())-(long)(b.getX()-a.getX())*(c.getZ()-b.getZ());}
}
