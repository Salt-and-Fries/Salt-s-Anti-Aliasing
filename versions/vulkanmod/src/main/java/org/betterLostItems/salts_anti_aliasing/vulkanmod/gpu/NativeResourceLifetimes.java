package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import java.util.*;

/** Tracks only VulkanMod-owned native objects omitted by its original shutdown path. */
public final class NativeResourceLifetimes {
    private record Key(String kind,long handle){}
    private record Resource(Key key,int order,Runnable destroy){}
    private static final Map<Key,Resource> live=new LinkedHashMap<>();
    private NativeResourceLifetimes(){}
    public static synchronized void created(String kind,long handle,int order,Runnable destroy){
        if(handle!=0)live.put(new Key(kind,handle),new Resource(new Key(kind,handle),order,destroy));
    }
    public static synchronized void destroyed(String kind,long handle){live.remove(new Key(kind,handle));}
    /** Queue owners perform their own cleanup after VMA has shut down. */
    public static void closeBeforeAllocator(){
        closeMatching(resource -> !Set.of("CommandPool", "Semaphore", "Fence").contains(resource.key().kind()));
    }
    public static void close(){ closeMatching(resource -> true); }
    private static void closeMatching(java.util.function.Predicate<Resource> selected){
        List<Resource> remaining;
        synchronized(NativeResourceLifetimes.class){remaining=new ArrayList<>(live.values());}
        remaining.sort(Comparator.comparingInt(Resource::order));
        for(var resource:remaining){
            if(!selected.test(resource))continue;
            synchronized(NativeResourceLifetimes.class){if(!live.remove(resource.key(),resource))continue;}
            resource.destroy().run();
        }
    }
}
