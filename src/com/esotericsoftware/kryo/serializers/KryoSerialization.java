
package com.esotericsoftware.kryo.serializers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.WeakHashMap;

import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.strategy.InstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer.CachedField;
import com.esotericsoftware.kryo.serializers.FieldSerializerGenericsUtil;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.esotericsoftware.kryo.serializers.ObjectField;
import com.esotericsoftware.kryo.serializers.UnsafeCacheFields.UnsafeObjectField;
import com.esotericsoftware.kryo.util.MapReferenceResolver;
import com.esotericsoftware.kryo.util.ObjectMap;
import com.esotericsoftware.kryo.util.ObjectMap.Entries;
import com.esotericsoftware.kryo.util.ObjectMap.Entry;
import com.esotericsoftware.reflectasm.FieldAccess;

/*** This class can be used to copy or serialize instances of Kryo or Kryo serializers.
 * @author Roman Levenstein <romixlev@gmail.com> */
public class KryoSerialization {

	static public class KryoSerializer extends Serializer<Kryo> {
		Kryo copier;

		public KryoSerializer () {
			copier = KryoSerialization.prepareKryoCopier();
		}

		public KryoSerializer (Kryo copier) {
			this.copier = copier;
		}

		public void write (Kryo kryo, Output output, Kryo object) {
			copier.writeObjectOrNull(output, object, Kryo.class);
		}

		public Kryo read (Kryo kryo, Input input, Class<Kryo> type) {
			Kryo newKryo = copier.readObjectOrNull(input, Kryo.class);
			newKryo.setClassLoader(Thread.currentThread().getContextClassLoader());
			newKryo.setReferenceResolver(new MapReferenceResolver());
			return newKryo;
		}

		public Kryo copy (Kryo kryo, Kryo original) {
			Kryo newKryo = copier.copy(original);
			newKryo.setClassLoader(Thread.currentThread().getContextClassLoader());
			newKryo.setReferenceResolver(new MapReferenceResolver());
			return newKryo;
		}
	}

	/*** Serializer for Kryo instances
	 * @author roman */
	static public class KryoInstanceSerializer extends FieldSerializer<Kryo> {
		{
			setAcceptsNull(true);
			setStateless(true);
		}

		public KryoInstanceSerializer (Kryo kryo, Class type) {
			super(kryo, type);
		}

		public Kryo read (Kryo kryo, Input input, Class type) {
			Kryo newKryo = (Kryo)super.read(kryo, input, type);
			// Set-up non serialized transient fields
			if (newKryo != null) {
				newKryo.setClassLoader(Thread.currentThread().getContextClassLoader());
				newKryo.setReferenceResolver(new MapReferenceResolver());
			}
			return newKryo;
		}

		public Kryo copy (Kryo kryo, Kryo original) {
			Kryo newKryo = (Kryo)super.copy(kryo, original);
			// Set-up non copied transient fields
			if (newKryo != null) {
				newKryo.setClassLoader(Thread.currentThread().getContextClassLoader());
				newKryo.setReferenceResolver(new MapReferenceResolver());
			}
			return newKryo;
		}
	}

	/*** Kryo serializer for ObjectMap. FieldSerializer cannot be used for ObjectMap, because it leads to problems when
	 * deserialization is performed by a different JVM instance.
	 * 
	 * @author roman */
	static public class ObjectMapSerializer extends Serializer<ObjectMap> {

		{
// setAcceptsNull(true);
			setStateless(true);
		}

		public void write (Kryo kryo, Output output, ObjectMap object) {
			Entries entries = new Entries(object);
			int size = object.size;
			output.writeInt(size, true);
			while (entries.hasNext()) {
				Entry entry = entries.next();
				kryo.writeClassAndObject(output, entry.key);
				kryo.writeClassAndObject(output, entry.value);
			}
		}

		public ObjectMap read (Kryo kryo, Input input, Class<ObjectMap> type) {
			int size = input.readInt(true);
			ObjectMap map = new ObjectMap();
			kryo.reference(map);
			for (int i = 0; i < size; i++) {
				Object key = kryo.readClassAndObject(input);
				Object value = kryo.readClassAndObject(input);
				map.put(key, value);
			}
			return map;
		}

		public ObjectMap copy (Kryo kryo, ObjectMap original) {
			ObjectMap map = new ObjectMap();
			kryo.reference(map);
			Entries entries = new Entries(original);
			while (entries.hasNext()) {
				Entry entry = entries.next();
				map.put(kryo.copy(entry.key), kryo.copy(entry.value));
			}
			return map;
		}
	}

	/*** Create a Kryo instance capable of serializing any other Kryo instance and any serializer.
	 * 
	 * @return */
	static public Kryo prepareKryoCopier () {
		boolean useRegistration = false;
		Kryo kryo = new Kryo();

		kryo.setAsmEnabled(true);
		// References should be enabled, as Kryo has a lot of internal circular dependencies
		kryo.setReferences(true);
		kryo.setRegistrationRequired(useRegistration);
		// Use standard instantiator strategy, because some classes do not provcide no-arg constructors
		kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
		// Use a dedicated serializer for serializing java.reflect.Field
// kryo.addDefaultSerializer(Field.class, JavaFieldSerializer.class);
		// Use a dedicated serializer for serializing java.reflect.Constructor
		kryo.addDefaultSerializer(Constructor.class, JavaConstructorSerializer.class);
		// Use a dedicated serializer for serializing Kryo serializers
		kryo.addDefaultSerializer(Serializer.class, SerializerSerializer.class);
		kryo.addDefaultSerializer(ObjectField.class, ObjectFieldSerializer.class);
		kryo.addDefaultSerializer(CachedField.class, CachedFieldSerializer.class);
		kryo.addDefaultSerializer(InstantiatorStrategy.class, InstantiatorStrategySerializer.class);
		// Use a dedicated serializer for serializing a DefaultInstantiatorStrategySerializer
// kryo.register(DefaultInstantiatorStrategy.class, new DefaultInstantiatorStrategySerializer());
// kryo.register(DefaultInstantiatorStrategy.class, new InstantiatorStrategySerializer());
		// Use FieldSerializer to serialize itself
		kryo.register(FieldSerializer.class, new FieldSerializer(kryo, FieldSerializer.class));
		kryo.register(Kryo.class, new KryoInstanceSerializer(kryo, Kryo.class));
		kryo.register(ObjectMap.class, new ObjectMapSerializer());
		kryo.register(Field.class, new JavaFieldSerializer());

		// Transient fields should not be copied for kryo internal classes
		// Usually, such fields are Kryo instances, FieldSerializer instances,
		// or ClassLoader instances, which are very expensive for copying

		// Do not copy transient fields in the following Kryo classes
		FieldSerializer fsSerializer;
		fsSerializer = (FieldSerializer)kryo.getRegistration(Kryo.class).getSerializer();
		fsSerializer.setCopyTransient(false);
		fsSerializer = (FieldSerializer)kryo.getRegistration(FieldSerializer.class).getSerializer();
		fsSerializer.setCopyTransient(false);
		Object objSerializer = kryo.getRegistration(UnsafeObjectField.class).getSerializer();
		if (objSerializer instanceof FieldSerializer) {
			fsSerializer = (FieldSerializer)objSerializer;
			fsSerializer.setCopyTransient(false);
		}
		objSerializer = kryo.getRegistration(ObjectField.class).getSerializer();
		if (objSerializer instanceof FieldSerializer) {
			fsSerializer = (FieldSerializer)objSerializer;
			fsSerializer.setCopyTransient(false);
		}
		fsSerializer = (FieldSerializer)kryo.getRegistration(FieldSerializerGenericsUtil.class).getSerializer();
		fsSerializer.setCopyTransient(false);

		return kryo;

	}

	/*** Serializer for constructors.
	 * @author roman */
	static public class JavaConstructorSerializer extends Serializer<Constructor> {
		{
			setImmutable(true);
			setAcceptsNull(true);
			// setStateless(true);
		}

		@Override
		public void write (Kryo kryo, Output output, Constructor object) {
			// Store class name and parameter types
			output.writeString(object.getDeclaringClass().getName());
			kryo.writeObjectOrNull(output, object.getParameterTypes(), Class[].class);
		}

		@Override
		public Constructor read (Kryo kryo, Input input, Class<Constructor> type) {
			try {
				// Read classname and parameter types
				String classname = input.readString();
				Class[] parameterTypes = kryo.readObjectOrNull(input, Class[].class);
				// Perform class lookup
				Class clazz = kryo.getClassLoader().loadClass(classname);
				// Perform constructor lookup
				Constructor constructor = clazz.getDeclaredConstructor(parameterTypes);
				constructor.setAccessible(true);
				return constructor;
			} catch (Exception e) {
				throw new KryoException(e);
			}
		}
	}

	/*** java.reflect.Field serializer
	 * @author roman */
	static public class JavaFieldSerializer extends Serializer<Field> {

		// Try to minimize reflction calls by caching
		transient Map<String, Class> nameToClass;
		transient Map<Class, Map<String, Field>> classToFields;

		{
			setImmutable(true);
			setAcceptsNull(true);
			// setStateless(true);
		}

		@Override
		public void write (Kryo kryo, Output output, Field object) {
			// Write class name and field name.
			String classname = object.getDeclaringClass().getName();
			kryo.writeObject(output, classname);
			kryo.writeObject(output, object.getName());
			putClassAndField(classname, object, object.getDeclaringClass());
		}

		@Override
		public Field read (Kryo kryo, Input input, Class<Field> type) {
			try {
				// Read class name and field name
				String classname = kryo.readObject(input, String.class);
				String fieldname = kryo.readObject(input, String.class);
				// Perform class lookup
				Class clazz = getClass(kryo, classname);
				// Perform field lookup
				Field field = getField(fieldname, clazz);
				field.setAccessible(true);
				return field;
			} catch (Exception e) {
				throw new KryoException(e);
			}
		}

		private Field getField (String fieldname, Class clazz) throws NoSuchFieldException {
			if (classToFields == null) classToFields = new WeakHashMap<Class, Map<String, Field>>();

			Map<String, Field> fields = classToFields.get(clazz);
			if (fields == null) {
				fields = new WeakHashMap<String, Field>();
				classToFields.put(clazz, fields);
			}

			Field field = fields.get(fieldname);
			if (field == null) {
				field = clazz.getDeclaredField(fieldname);
				fields.put(fieldname, field);
			}
			return field;
		}

		private void putClassAndField (String classname, Field field, Class clazz) {
			if (nameToClass == null) nameToClass = new WeakHashMap<String, Class>();
			nameToClass.put(classname, clazz);
			if (classToFields == null) classToFields = new WeakHashMap<Class, Map<String, Field>>();
			Map<String, Field> fields = classToFields.get(clazz);
			if (fields == null) {
				fields = new WeakHashMap<String, Field>();
				classToFields.put(clazz, fields);
			}
			fields.put(field.getName(), field);
		}

		/*** Cache classes, because classloader lookups are very expensive.
		 * @param kryo
		 * @param classname
		 * @return
		 * @throws ClassNotFoundException */
		private Class getClass (Kryo kryo, String classname) throws ClassNotFoundException {
			Class c = null;
			if (nameToClass == null) nameToClass = new WeakHashMap<String, Class>();
			c = nameToClass.get(classname);
			if (c == null) {
				c = kryo.getClassLoader().loadClass(classname);
				nameToClass.put(classname, c);
			}
			return c;
		}
	}

	static public class CachingInstantiatorStrategy implements InstantiatorStrategy {
		private InstantiatorStrategy strategy;
		// Try to cache stratagies and instantiators to avoid reflection calls
		transient private Map<Class, ObjectInstantiator> classToInstantiator;
		static Map<Class<InstantiatorStrategy>, CachingInstantiatorStrategy> instances = new Hashtable<Class<InstantiatorStrategy>, CachingInstantiatorStrategy>();

		static public CachingInstantiatorStrategy getInstance (Kryo kryo, Class type) {
			CachingInstantiatorStrategy instance = instances.get(type);
			if (instance == null) {
				instance = new CachingInstantiatorStrategy((InstantiatorStrategy)kryo.newInstance(type));
				instances.put(type, instance);
			}
			return instance;
		}

		public CachingInstantiatorStrategy (InstantiatorStrategy strategy) {
			this.strategy = strategy;
		}

		public ObjectInstantiator newInstantiatorOf (Class type) {
			ObjectInstantiator instantiator = getInstantiator(type);
			return instantiator;
		}

		private ObjectInstantiator getInstantiator (Class type) {
			if (classToInstantiator == null) classToInstantiator = new WeakHashMap<Class, ObjectInstantiator>();
			ObjectInstantiator instantiator = classToInstantiator.get(type);
			if (instantiator == null) {
				instantiator = strategy.newInstantiatorOf(type);
				classToInstantiator.put(type, instantiator);
			}
			return instantiator;
		}

	}

	/*** InstantiatorStrategy serializer
	 * @author roman */
	static public class InstantiatorStrategySerializer extends Serializer<InstantiatorStrategy> {
		{
			setImmutable(true);
			setAcceptsNull(true);
			// setStateless(true);
		}

		@Override
		public void write (Kryo kryo, Output output, InstantiatorStrategy strategy) {
			// Don't do anything
			if (strategy instanceof CachingInstantiatorStrategy) {
				output.write(1);
				kryo.writeClassAndObject(output, ((CachingInstantiatorStrategy)strategy).strategy);
			} else {
				output.write(0);
			}
		}

		@Override
		public InstantiatorStrategy read (Kryo kryo, Input input, Class<InstantiatorStrategy> type) {
			int mode = input.read();
			if (mode == 1) return (InstantiatorStrategy)kryo.readClassAndObject(input);
			// Simply create a new default instantiator strategy
			CachingInstantiatorStrategy strategy = CachingInstantiatorStrategy.getInstance(kryo, type);
			return strategy;
		}
	}

	/*** Serializer for Kryo's serializers
	 * @author roman
	 * 
	 * @param <T> */
	static public class SerializerSerializer<T extends Serializer> extends Serializer<T> {

		private Map<Class, Serializer> serializerToFieldSerializer = new HashMap();

		@Override
		public T copy (Kryo kryo, T original) {
			if (false && original.isStateless()) {
				return original;
			}
// if(kryo.getClassResolver().getRegistration(original.getClass()) == null)
// kryo.register(original.getClass(), new FieldSerializer(kryo, original.getClass()));
			Serializer fieldSerializer = getFieldSerializer(kryo, original.getClass());
			return (T)fieldSerializer.copy(kryo, original);
// return kryo.copy(original);
		}

		{
// setImmutable(true);
// setAcceptsNull(true);
			// setStateless(true);
		}

		@Override
		public void write (Kryo kryo, Output output, T serializer) {
			// Check if a serializer for this serializer is known already
			Serializer fieldSerializer = getFieldSerializer(kryo, serializer.getClass());

			// Use a dedicated serializer
			fieldSerializer.write(kryo, output, serializer);
		}

		private Serializer getFieldSerializer (Kryo kryo, Class<? extends Serializer> clazz) {
			Serializer fieldSerializer = serializerToFieldSerializer.get(clazz);
			if (fieldSerializer == null) {
				if (clazz == FieldSerializer.class) {
					// For FieldSerializer, use a registered serializer
					fieldSerializer = kryo.getRegistration(FieldSerializer.class).getSerializer();
					if (!(fieldSerializer instanceof FieldSerializer)) fieldSerializer = new FieldSerializer(kryo, clazz);
				} else {
					// For everything else create a dedicated FieldSerializer
					fieldSerializer = new FieldSerializer(kryo, clazz);
				}
				serializerToFieldSerializer.put(clazz, fieldSerializer);
			}
			return fieldSerializer;
		}

		@Override
		public T read (Kryo kryo, Input input, Class<T> type) {
			// Check if a serializer for this serializer is known already
			Serializer fieldSerializer = getFieldSerializer(kryo, type);
			T serializer = (T)fieldSerializer.read(kryo, input, type);
			return serializer;
		}
	}

	/*** Serializer for cached fields. It tries to serialize cached fields in a format that is not dependent on the real
	 * implementation of concrete CacheField-derived classes. For example, it shouldn't matter, if a given field is implemented as
	 * a AsmXXXField, XXXObjectField or UnsafeXXXField.
	 * 
	 * @author roman */
	public static class CachedFieldSerializer extends Serializer<CachedField> {
		{
			setAcceptsNull(true);
			setStateless(true);
		}

		public CachedFieldSerializer () {

		}

		@Override
		public void write (Kryo kryo, Output output, CachedField cachedField) {
			output.writeLong(cachedField.offset);
			output.writeInt(cachedField.accessIndex);
			output.writeBoolean(cachedField.canBeNull);
			output.writeBoolean(cachedField.varIntsEnabled);
			kryo.writeObjectOrNull(output, cachedField.valueClass, Class.class);
			kryo.writeClassAndObject(output, cachedField.serializer);
			kryo.writeClassAndObject(output, cachedField.field);
			kryo.writeClassAndObject(output, cachedField.access);
		}

		@Override
		public CachedField read (Kryo kryo, Input input, Class<CachedField> type) {
			CachedField cachedField = kryo.newInstance(type);
			cachedField.offset = input.readLong();
			cachedField.accessIndex = input.readInt();
			cachedField.canBeNull = input.readBoolean();
			cachedField.varIntsEnabled = input.readBoolean();
			cachedField.valueClass = kryo.readObjectOrNull(input, Class.class);
			cachedField.serializer = (Serializer)kryo.readClassAndObject(input);
			cachedField.field = (Field)kryo.readClassAndObject(input);
			cachedField.access = (FieldAccess)kryo.readClassAndObject(input);
			return cachedField;
		}

		public CachedField copy (Kryo kryo, CachedField original) {
			CachedField cachedField = kryo.newInstance(original.getClass());
			cachedField.offset = original.offset;
			cachedField.accessIndex = original.accessIndex;
			cachedField.canBeNull = original.canBeNull;
			cachedField.varIntsEnabled = original.varIntsEnabled;
			cachedField.valueClass = kryo.copy(original.valueClass);
			cachedField.serializer = kryo.copy(original.serializer);
			cachedField.field = kryo.copy(original.field);
			cachedField.access = kryo.copy(original.access);
			return cachedField;
		}
	}

	public static class ObjectFieldSerializer extends CachedFieldSerializer {

		public ObjectFieldSerializer () {

		}

		@Override
		public void write (Kryo kryo, Output output, CachedField cachedField) {
			super.write(kryo, output, cachedField);
			ObjectField objectField = (ObjectField)cachedField;
			kryo.writeClassAndObject(output, objectField.kryo);
			kryo.writeClassAndObject(output, objectField.fieldSerializer);
			kryo.writeClassAndObject(output, objectField.generics);
			kryo.writeClassAndObject(output, objectField.type);
		}

		@Override
		public CachedField read (Kryo kryo, Input input, Class<CachedField> type) {
			ObjectField objectField = (ObjectField)super.read(kryo, input, type);
			objectField.kryo = (Kryo)kryo.readClassAndObject(input);
			objectField.fieldSerializer = (FieldSerializer)kryo.readClassAndObject(input);
			objectField.generics = (Class[])kryo.readClassAndObject(input);
			objectField.type = (Class)kryo.readClassAndObject(input);
			return objectField;
		}

		public CachedField copy (Kryo kryo, CachedField original) {
			ObjectField objectField = (ObjectField)super.copy(kryo, original);
			ObjectField originalField = (ObjectField)original;
			objectField.kryo = kryo.copy(originalField.kryo);
			objectField.fieldSerializer = kryo.copy(originalField.fieldSerializer);
			objectField.generics = kryo.copy(originalField.generics);
			objectField.type = kryo.copy(originalField.type);
			return objectField;
		}

	}
}
