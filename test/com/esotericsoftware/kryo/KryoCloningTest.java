
package com.esotericsoftware.kryo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.objenesis.strategy.StdInstantiatorStrategy;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.KryoSerialization;
import com.esotericsoftware.kryo.serializers.KryoSerialization.KryoSerializer;
import com.esotericsoftware.kryo.util.MapReferenceResolver;
import com.esotericsoftware.minlog.Log;
//import com.esotericsoftware.kryo.serializers.FieldSerializerGenericsUtil;
//import com.esotericsoftware.kryo.serializers.UnsafeCacheFields.UnsafeObjectField;

/** @author Nathan Sweet <misc@n4te.com> */
public class KryoCloningTest extends KryoTestCase {
	private static final int MAX_COPIES = 1;
	private boolean checkCorrectness = false;

	{
		supportsCopy = true;
	}


	/*** For copying: some fields need to be transient, e.g. all fields of type Kryo and all fields of type FieldSerializer and
	 * ClassLoader.
	 * 
	 * For serialization, only Kryo.classLoader has to be declared transient.
	 * 
	 * @throws CloneNotSupportedException */
	public void testKryoInstanceKryoCopyingSpeed () throws CloneNotSupportedException {

		DefaultTypes test = createTestObject();

		Log.ERROR();

		Kryo kryo1 = new Kryo();
		kryo1.register(DefaultTypes.class);
		kryo1.register(HasGenerics.class);
		kryo = kryo1;
		roundTrip(81, 91, test);

		FieldSerializer serializer = (FieldSerializer)kryo1.getRegistration(DefaultTypes.class).getSerializer();

		Kryo origKryo = KryoSerialization.prepareKryoCopier();

		for (int i = 0; i < MAX_COPIES; i++) {
			if(checkCorrectness) {
				kryo = kryo1;
				roundTrip(81, 91, test);
			}
			Kryo inKryo1 = kryoCreateCopy(origKryo, kryo1);
			if (checkCorrectness) {
				kryo = inKryo1;
				roundTrip(81, 91, test);
			}
		}
	}

	/***
	 * Copy Kryo instances by means of serialization, i.e. by first serializing
	 * and then deserializing it.
	 * 
	 * @throws CloneNotSupportedException
	 */
	public void testKryoInstanceSelfCopyingSpeedUsingSerialization () throws CloneNotSupportedException {
		Log.ERROR();

		Kryo origKryo = KryoSerialization.prepareKryoCopier();
		Kryo origKryo1 = KryoSerialization.prepareKryoCopier();
		Kryo origKryo2 = KryoSerialization.prepareKryoCopier();

		// Copy unused KryoCopier
		kryoCreateCopy(origKryo1, origKryo2);
		kryoCreateCopy(origKryo, origKryo2);
		// Copy used KryoCopier using used KryoCopier
		Kryo inOrigKryo1 = kryoCreateCopy(origKryo, origKryo1);

		Kryo lastKryo = inOrigKryo1;
		Kryo prevKryo = origKryo;
		for (int i = 0; i < MAX_COPIES; i++) {
			Kryo prev = lastKryo;
			lastKryo = kryoCreateCopy(lastKryo, prevKryo);
			prevKryo = prev;
		}
	}

	/***
	 * Copy Kryo instances by means of "copy" method. This approach typically
	 * avoids serialization and deserialization and is much faster.
	 * @throws CloneNotSupportedException
	 */
	public void testKryoInstanceSelfCopyingSpeed () throws CloneNotSupportedException {
		Log.ERROR();
		
		Kryo origKryo = KryoSerialization.prepareKryoCopier();
		Kryo origKryo1 = KryoSerialization.prepareKryoCopier();
		Kryo origKryo2 = KryoSerialization.prepareKryoCopier();

		// Copy unused KryoCopier
		kryoCreateCopy(origKryo1, origKryo2);
		kryoCreateCopy(origKryo, origKryo2);
		// Copy used KryoCopier using used KryoCopier
		Kryo inOrigKryo1 = kryoCreateCopy(origKryo, origKryo1);

		Kryo lastKryo = inOrigKryo1;
		Kryo prevKryo = origKryo;
		for (int i = 0; i < MAX_COPIES; i++) {
			Kryo prev = lastKryo;
			lastKryo = lastKryo.copy(prevKryo);
			prevKryo = prev;
		}
	}
	
	/***
	 * Write kryo instance "obj" by means of Kryo instance kryo and then read it back.
	 * It creates a copy of "obj".
	 * @param kryo
	 * @param obj
	 * @return
	 */
	private Kryo kryoCreateCopy (Kryo kryo, Kryo obj) {
		output = new Output(1024 * 60);
		kryo.writeObject(output, obj);
		output.flush();
		byte[] inputBytes = output.toBytes();
// System.err.println("Serialized Kryo instance size is " + inputBytes.length);
		Input input = new Input(inputBytes);
		Kryo inKryo = kryo.readObject(input, Kryo.class);
		inKryo.setClassLoader(Thread.currentThread().getContextClassLoader());
		inKryo.setReferenceResolver(new MapReferenceResolver());

		// Compare kryos
// assertEquals(obj.getAsmEnabled(), inKryo.getAsmEnabled());
// assertEquals(obj.getNextRegistrationId(), inKryo.getNextRegistrationId());
// assertEquals(obj.getReferences(), inKryo.getReferences());
// assertEquals(obj.isRegistrationRequired(), inKryo.isRegistrationRequired());
// assertEquals(obj.getClassResolver(), inKryo.getClassResolver());
		return inKryo;
	}

	public void testFieldSerializerKryoCopyingSpeed1 () throws CloneNotSupportedException {
		Log.ERROR();
		KryoSerializer kryoSerializer = new KryoSerialization.KryoSerializer();
		Serializer serializerSerializer = new KryoSerialization.SerializerSerializer<Serializer>();
		kryo.setRegistrationRequired(false);
		kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
		kryo.register(Kryo.class, kryoSerializer);
		kryo.addDefaultSerializer(Serializer.class, serializerSerializer);
		//kryo.register(FieldSerializer.class, new FieldSerializer(kryo, FieldSerializer.class));
		
		Kryo customKryo = new Kryo();
		customKryo.register(DefaultTypes.class);
		customKryo.register(HasGenerics.class);

		FieldSerializer serializer = (FieldSerializer)customKryo.getRegistration(DefaultTypes.class).getSerializer();
		Kryo newKryo = new Kryo();
		for (int i = 0; i < MAX_COPIES; i++) {
			// Copy without transient fields. Patch these fields
			// afterwards....
			// TODO: How such fields can be patched?
			FieldSerializer clonedSerializer = kryo.copy(serializer);
			assertNotNull(clonedSerializer);
			newKryo.register(DefaultTypes.class, clonedSerializer);
			output = new Output(1024*10);
			newKryo.writeObject(output, createTestObject());
		}
	}
		
	/***
	 * Test how fast a serializer can be re-created.
	 * @throws CloneNotSupportedException
	 */
	public void testFieldSerializerCreationSpeed () throws CloneNotSupportedException {
		Log.ERROR();
		kryo.register(DefaultTypes.class);
		kryo.register(HasGenerics.class);
		FieldSerializer serializer = (FieldSerializer)kryo.getRegistration(DefaultTypes.class).getSerializer();
		for (int i = 0; i < MAX_COPIES; i++) {
			Serializer clonedSerializer = new FieldSerializer(kryo, DefaultTypes.class);
			assertNotNull(clonedSerializer);
		}
	}

	/***
	 * Test how fast a Kryo instance can be re-created.
	 * @throws CloneNotSupportedException
	 */
	public void testKryoCreationSpeed () throws CloneNotSupportedException {
		Log.ERROR();
		kryo.register(DefaultTypes.class);
		kryo.register(HasGenerics.class);
		for (int i = 0; i < MAX_COPIES; i++) {
			Kryo clonedKryo = new Kryo();
			assertNotNull(clonedKryo);
			clonedKryo.register(DefaultTypes.class);
			clonedKryo.register(HasGenerics.class);
		}
	}

	
	public void testKryoPersist() throws Exception {
		Log.ERROR();
		
		Kryo origKryo = KryoSerialization.prepareKryoCopier();
		Kryo origKryo1 = KryoSerialization.prepareKryoCopier();
		Kryo origKryo2 = KryoSerialization.prepareKryoCopier();

		// Copy unused KryoCopier
		kryoCreateCopy(origKryo1, origKryo2);
		kryoCreateCopy(origKryo, origKryo2);

		DefaultTypes test = createTestObject();
		Kryo customKryo = new Kryo();
		customKryo.register(DefaultTypes.class);
		customKryo.register(HasGenerics.class);
		kryo = customKryo;
		roundTrip(81, 91, test);
		
		FileOutputStream fos = new FileOutputStream("kryo.ser");
		output = new Output(fos);
//		Log.TRACE();
		origKryo.writeObject(output, customKryo);
		output.flush();
		output.close();
		Log.ERROR();
		
		Kryo customKryo2 = kryoCreateCopy(origKryo, customKryo);
		

		kryo = customKryo;
		roundTrip(81, 91, test);
		roundTrip(81, 91, test);
		roundTrip(81, 91, test);

		kryo = customKryo2;
		roundTrip(81, 91, test);
		
		fos = new FileOutputStream("kryocopier.ser");
		output = new Output(fos);
//		Log.TRACE();
		origKryo.writeObject(output, origKryo1);
		output.flush();
		output.close();
	}
	
	public void testKryoRestore() throws Exception {
		Log.ERROR();
		
		Kryo origKryo = KryoSerialization.prepareKryoCopier();		
		FileInputStream fos = new FileInputStream("kryo.ser");
		input = new Input(fos);
//		Log.TRACE();
		Kryo customKryo = origKryo.readObject(input, Kryo.class);
		Log.ERROR();
		
		DefaultTypes test = createTestObject();
		Log.ERROR();
		kryo = customKryo;
		roundTrip(81, 91, test);
	}

	private DefaultTypes createTestObject () {
		DefaultTypes test = new DefaultTypes();
		test.booleanField = true;
		test.byteField = 123;
		test.charField = 'Z';
		test.shortField = 12345;
		test.intField = 123456;
		test.longField = 123456789;
		test.floatField = 123.456f;
		test.doubleField = 1.23456d;
		test.BooleanField = true;
		test.ByteField = -12;
		test.CharacterField = 'X';
		test.ShortField = -12345;
		test.IntegerField = -123456;
		test.LongField = -123456789l;
		test.FloatField = -123.3f;
		test.DoubleField = -0.121231d;
		test.StringField = "stringvalue";
		test.byteArrayField = new byte[] {2, 1, 0, -1, -2};
		return test;
	}
	
	static public class DefaultTypes {
		// Primitives.
		public boolean booleanField;
		public byte byteField;
		public char charField;
		public short shortField;
		public int intField;
		public long longField;
		public float floatField;
		public double doubleField;
		// Primitive wrappers.
		public Boolean BooleanField;
		public Byte ByteField;
		public Character CharacterField;
		public Short ShortField;
		public Integer IntegerField;
		public Long LongField;
		public Float FloatField;
		public Double DoubleField;
		// Other.
		public String StringField;
		public byte[] byteArrayField;

		DefaultTypes child;
		HasStringField hasStringField;

		public boolean equals (Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			DefaultTypes other = (DefaultTypes)obj;
			if (BooleanField == null) {
				if (other.BooleanField != null) return false;
			} else if (!BooleanField.equals(other.BooleanField)) return false;
			if (ByteField == null) {
				if (other.ByteField != null) return false;
			} else if (!ByteField.equals(other.ByteField)) return false;
			if (CharacterField == null) {
				if (other.CharacterField != null) return false;
			} else if (!CharacterField.equals(other.CharacterField)) return false;
			if (DoubleField == null) {
				if (other.DoubleField != null) return false;
			} else if (!DoubleField.equals(other.DoubleField)) return false;
			if (FloatField == null) {
				if (other.FloatField != null) return false;
			} else if (!FloatField.equals(other.FloatField)) return false;
			if (IntegerField == null) {
				if (other.IntegerField != null) return false;
			} else if (!IntegerField.equals(other.IntegerField)) return false;
			if (LongField == null) {
				if (other.LongField != null) return false;
			} else if (!LongField.equals(other.LongField)) return false;
			if (ShortField == null) {
				if (other.ShortField != null) return false;
			} else if (!ShortField.equals(other.ShortField)) return false;
			if (StringField == null) {
				if (other.StringField != null) return false;
			} else if (!StringField.equals(other.StringField)) return false;
			if (booleanField != other.booleanField) return false;

			Object list1 = arrayToList(byteArrayField);
			Object list2 = arrayToList(other.byteArrayField);
			if (list1 != list2) {
				if (list1 == null || list2 == null) return false;
				if (!list1.equals(list2)) return false;
			}

			if (child != other.child) {
				if (child == null || other.child == null) return false;
				if (child != this && !child.equals(other.child)) return false;
			}

			if (byteField != other.byteField) return false;
			if (charField != other.charField) return false;
			if (Double.doubleToLongBits(doubleField) != Double.doubleToLongBits(other.doubleField)) return false;
			if (Float.floatToIntBits(floatField) != Float.floatToIntBits(other.floatField)) return false;
			if (intField != other.intField) return false;
			if (longField != other.longField) return false;
			if (shortField != other.shortField) return false;
			return true;
		}
	}

	static public class HasStringField {
		public String text;

		public boolean equals (Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			HasStringField other = (HasStringField)obj;
			if (text == null) {
				if (other.text != null) return false;
			} else if (!text.equals(other.text)) return false;
			return true;
		}
	}

	static public class HasGenerics {
		ArrayList<Integer> list1;
		List<List<?>> list2 = new ArrayList<List<?>>();
		List<?> list3 = new ArrayList();
		ArrayList<?> list4 = new ArrayList();
		ArrayList<String> list5;
		HashMap<String, ArrayList<Integer>> map1;

		public boolean equals (Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			HasGenerics other = (HasGenerics)obj;
			if (list1 == null) {
				if (other.list1 != null) return false;
			} else if (!list1.equals(other.list1)) return false;
			if (list2 == null) {
				if (other.list2 != null) return false;
			} else if (!list2.equals(other.list2)) return false;
			if (list3 == null) {
				if (other.list3 != null) return false;
			} else if (!list3.equals(other.list3)) return false;
			if (list4 == null) {
				if (other.list4 != null) return false;
			} else if (!list4.equals(other.list4)) return false;
			if (list5 == null) {
				if (other.list5 != null) return false;
			} else if (!list5.equals(other.list5)) return false;
			if (map1 == null) {
				if (other.map1 != null) return false;
			} else if (!map1.equals(other.map1)) return false;
			return true;
		}
	}
}
