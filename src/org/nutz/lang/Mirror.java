package org.nutz.lang;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nutz.castor.Castors;
import org.nutz.castor.FailToCastObjectException;
import org.nutz.lang.born.Borning;
import org.nutz.lang.inject.Injecting;
import org.nutz.lang.inject.InjectByField;
import org.nutz.lang.inject.InjectBySetter;

/**
 * 包裹了 Class<?>， 提供了更多的反射方法
 * 
 * @author zozoh(zozohtnt@gmail.com)
 * 
 * @param <T>
 */
public class Mirror<T> {

	private static class DefaultTypeExtractor implements TypeExtractor {

		public Class<?>[] extract(Mirror<?> mirror) {
			Class<?> theType = mirror.getType();
			List<Class<?>> re = new ArrayList<Class<?>>(5);

			// 原生类型，增加其外覆类
			if (theType.isPrimitive()) {
				re.add(mirror.getWrapperClass());
				// 数字
				if (theType != boolean.class && theType != char.class) {
					re.add(Number.class);
				}
			}
			// 日历
			else if (mirror.isOf(Calendar.class)) {
				re.add(Calendar.class);
			}
			// 其他类型，直接增加，并试图判断其抽象类
			else {
				re.add(theType);
				// 枚举
				if (mirror.klass.isEnum()) {
					re.add(Enum.class);
				}
				// 数组
				else if (mirror.klass.isArray()) {
					re.add(Array.class);
				}
				// 字符串
				else if (mirror.isStringLike())
					re.add(CharSequence.class);
				// 数字
				else if (mirror.isNumber()) {
					re.add(Number.class);
				}
				// Map
				else if (mirror.isOf(Map.class)) {
					re.add(Map.class);
				}
				// 列表
				else if (mirror.isOf(List.class)) {
					re.add(List.class);
					re.add(Collection.class);
				}
				// 集合
				else if (mirror.isOf(Collection.class)) {
					re.add(Collection.class);
				}
			}
			// 最后确保 Object 一定被加上了
			if (theType != Object.class)
				re.add(Object.class);

			return re.toArray(new Class<?>[re.size()]);
		}

	}

	private final static DefaultTypeExtractor defaultTypeExtractor = new DefaultTypeExtractor();

	/**
	 * 包裹一个类
	 * 
	 * @param classOfT
	 *            类
	 * @return Mirror
	 */
	public static <T> Mirror<T> me(Class<T> classOfT) {
		return null == classOfT	? null
								: new Mirror<T>(classOfT).setTypeExtractor(defaultTypeExtractor);
	}

	/**
	 * 生成一个对象的 Mirror
	 * 
	 * @param obj
	 *            对象。
	 * @return Mirror， 如果 对象 null，则返回 null
	 */
	@SuppressWarnings("unchecked")
	public static <T> Mirror<T> me(T obj) {
		return null == obj ? null : (Mirror<T>) me(obj.getClass());
	}

	/**
	 * 包裹一个类，并设置自定义的类型提炼逻辑
	 * 
	 * @param classOfT
	 * @param typeExtractor
	 * @return Mirror
	 * @see org.nutz.lang.TypeExtractor
	 */
	public static <T> Mirror<T> me(Class<T> classOfT, TypeExtractor typeExtractor) {
		return null == classOfT	? null
								: new Mirror<T>(classOfT).setTypeExtractor(typeExtractor == null ? defaultTypeExtractor
																								: typeExtractor);
	}

	private Class<T> klass;

	private TypeExtractor typeExtractor;

	/**
	 * 设置自己的类型提炼逻辑
	 * 
	 * @param typeExtractor
	 * @return Mirror
	 * @see org.nutz.lang.TypeExtractor
	 */
	public Mirror<T> setTypeExtractor(TypeExtractor typeExtractor) {
		this.typeExtractor = typeExtractor;
		return this;
	}

	private Mirror(Class<T> classOfT) {
		klass = classOfT;
	}

	/**
	 * 根据名称获取一个 Getter。
	 * <p>
	 * 比如，你想获取 abc 的 getter ，那么优先查找 getAbc()，如果没有则查找isAbc()，最后才是查找 abc()。
	 * 
	 * @param fieldName
	 * @return 方法
	 * @throws NoSuchMethodException
	 *             没有找到 Getter
	 */
	public Method getGetter(String fieldName) throws NoSuchMethodException {
		try {
			String fn = Strings.capitalize(fieldName);
			try {
				try {
					return klass.getMethod("get" + fn);
				}
				catch (NoSuchMethodException e) {
					Method m = klass.getMethod("is" + fn);
					if (!Mirror.me(m.getReturnType()).isBoolean())
						throw new NoSuchMethodException();
					return m;
				}
			}
			catch (NoSuchMethodException e) {
				return klass.getMethod(fieldName);
			}
		}
		catch (RuntimeException e) {
			throw Lang.makeThrow(	NoSuchMethodException.class,
									"Fail to find getter for [%s]->[%s]",
									klass.getName(),
									fieldName);
		}
	}

	/**
	 * 根据字段获取一个 Getter。
	 * <p>
	 * 比如，你想获取 abc 的 getter ，那么优先查找 getAbc()，如果 没有，则查找 abc()。
	 * 
	 * @param field
	 * @return 方法
	 * @throws NoSuchMethodException
	 *             没有找到 Getter
	 */
	public Method getGetter(Field field) throws NoSuchMethodException {
		try {
			try {
				String fn = Strings.capitalize(field.getName());
				if (Mirror.me(field.getType()).isBoolean())
					return klass.getMethod("is" + fn);
				else
					return klass.getMethod("get" + fn);
			}
			catch (NoSuchMethodException e) {
				return klass.getMethod(field.getName());
			}
		}
		catch (Exception e) {
			throw Lang.makeThrow(	NoSuchMethodException.class,
									"Fail to find getter for [%s]->[%s]",
									klass.getName(),
									field.getName());
		}
	}

	/**
	 * 根据一个字段获取 Setter
	 * <p>
	 * 比如，你想获取 abc 的 setter ，那么优先查找 setAbc(T abc)，如果 没有，则查找 abc(T abc)。
	 * 
	 * @param field
	 *            字段
	 * @return 方法
	 * @throws NoSuchMethodException
	 *             没找到 Setter
	 */
	public Method getSetter(Field field) throws NoSuchMethodException {
		try {
			try {
				return klass.getMethod("set" + Strings.capitalize(field.getName()), field.getType());
			}
			catch (RuntimeException e) {
				try {
					if (field.getName().startsWith("is") && Mirror.me(field.getType()).isBoolean())
						return klass.getMethod(	"set" + field.getName().substring(2),
												field.getType());
				}
				catch (RuntimeException e1) {}
				return klass.getMethod(field.getName(), field.getType());
			}
		}
		catch (RuntimeException e) {
			throw Lang.makeThrow(	NoSuchMethodException.class,
									"Fail to find setter for [%s]->[%s]",
									klass.getName(),
									field.getName());
		}
	}

	/**
	 * 根据一个字段名和字段类型获取 Setter
	 * 
	 * @param fieldName
	 *            字段名
	 * @param paramType
	 *            字段类型
	 * @return 方法
	 * @throws NoSuchMethodException
	 *             没找到 Setter
	 */
	public Method getSetter(String fieldName, Class<?> paramType) throws NoSuchMethodException {
		try {
			String setterName = "set" + Strings.capitalize(fieldName);
			try {
				return klass.getMethod(setterName, paramType);
			}
			catch (RuntimeException e) {
				try {
					return klass.getMethod(fieldName, paramType);
				}
				catch (RuntimeException e1) {
					Mirror<?> type = Mirror.me(paramType);
					for (Method method : klass.getMethods()) {
						if (method.getParameterTypes().length == 1)
							if (method.getName().equals(setterName)
								|| method.getName().equals(fieldName)) {
								if (null == paramType
									|| type.canCastToDirectly(method.getParameterTypes()[0]))
									return method;
							}
					}
					throw new RuntimeException();
				}
			}
		}
		catch (RuntimeException e) {
			throw Lang.makeThrow(	NoSuchMethodException.class,
									"Fail to find setter for [%s]->[%s(%s)]",
									klass.getName(),
									fieldName,
									paramType.getName());
		}
	}

	/**
	 * 根据一个字段名，获取一组有可能成为 Setter 函数
	 * 
	 * @param fieldName
	 * @return 函数数组
	 */
	public Method[] findSetters(String fieldName) {
		String mName = "set" + Strings.capitalize(fieldName);
		List<Method> ms = new ArrayList<Method>();
		for (Method m : this.klass.getMethods()) {
			if (!Modifier.isStatic(m.getModifiers())
				&& m.getParameterTypes().length == 1
				&& m.getName().equals(mName))
				ms.add(m);
		}
		return ms.toArray(new Method[ms.size()]);
	}

	/**
	 * 获取一个字段。这个字段可以是当前类型或者其父类的私有字段。
	 * 
	 * @param name
	 *            字段名
	 * @return 字段
	 * @throws NoSuchFieldException
	 */
	public Field getField(String name) throws NoSuchFieldException {
		Class<?> cc = klass;
		while (null != cc && cc != Object.class) {
			try {
				return cc.getDeclaredField(name);
			}
			catch (NoSuchFieldException e) {
				cc = cc.getSuperclass();
			}
		}
		throw new NoSuchFieldException(String.format(	"Can NOT find field [%s] in class [%s] and it's parents classes",
														name,
														klass.getName()));
	}

	/**
	 * 获取一个字段。这个字段必须声明特殊的注解，第一遇到的对象会被返回
	 * 
	 * @param ann
	 *            注解
	 * @return 字段
	 * @throws NoSuchFieldException
	 */
	public <AT extends Annotation> Field getField(Class<AT> ann) throws NoSuchFieldException {
		for (Field field : this.getFields()) {
			if (field.isAnnotationPresent(ann))
				return field;
		}
		throw new NoSuchFieldException(String.format(	"Can NOT find field [@%s] in class [%s] and it's parents classes",
														ann.getName(),
														klass.getName()));
	}

	/**
	 * 获取一组声明了特殊注解的字段
	 * 
	 * @param ann
	 *            注解类型
	 * @return 字段数组
	 */
	public <AT extends Annotation> Field[] getFields(Class<AT> ann) {
		List<Field> fields = new LinkedList<Field>();
		for (Field f : this.getFields()) {
			if (f.isAnnotationPresent(ann))
				fields.add(f);
		}
		return fields.toArray(new Field[fields.size()]);
	}

	private static boolean isIgnoredField(Field f) {
		return Modifier.isStatic(f.getModifiers())
				|| Modifier.isFinal(f.getModifiers())
				|| f.getName().startsWith("this$");
	}

	/**
	 * 获得所有的属性，包括私有属性。不包括 Object 的属性
	 */
	public Field[] getFields() {
		Class<?> cc = klass;
		Map<String, Field> map = new HashMap<String, Field>();
		while (null != cc && cc != Object.class) {
			Field[] fs = cc.getDeclaredFields();
			for (int i = 0; i < fs.length; i++) {
				if (!isIgnoredField(fs[i]) && !map.containsKey(fs[i].getName()))
					map.put(fs[i].getName(), fs[i]);
			}
			cc = cc.getSuperclass();
		}
		return map.values().toArray(new Field[map.size()]);
	}

	/**
	 * 获取本类型所有的方法，包括私有方法。不包括 Object 的方法
	 */
	public Method[] getMethods() {
		Class<?> cc = klass;
		List<Method> list = new LinkedList<Method>();
		while (null != cc && cc != Object.class) {
			Method[] ms = cc.getDeclaredMethods();
			for (int i = 0; i < ms.length; i++) {
				list.add(ms[i]);
			}
			cc = cc.getSuperclass();
		}
		return list.toArray(new Method[list.size()]);
	}

	/**
	 * 获取当前对象，所有的方法，包括私有方法。递归查找至自己某一个父类为止 。
	 * <p>
	 * 并且这个按照名称，消除重复的方法。子类方法优先
	 * 
	 * @param top
	 *            截至的父类
	 * @return 方法数组
	 */
	public Method[] getAllDeclaredMethods(Class<?> top) {
		Class<?> cc = klass;
		Map<String, Method> map = new HashMap<String, Method>();
		while (null != cc && cc != Object.class) {
			Method[] fs = cc.getDeclaredMethods();
			for (int i = 0; i < fs.length; i++) {
				String key = fs[i].getName() + Mirror.getParamDescriptor(fs[i].getParameterTypes());
				if (!map.containsKey(key))
					map.put(key, fs[i]);
			}
			cc = cc.getSuperclass() == top ? null : cc.getSuperclass();
		}
		return map.values().toArray(new Method[map.size()]);
	}

	/**
	 * 相当于 getAllDeclaredMethods(Object.class)
	 * 
	 * @return 方法数组
	 */
	public Method[] getAllDeclaredMethodsWithoutTop() {
		return getAllDeclaredMethods(Object.class);
	}

	/**
	 * @return 所有静态方法
	 */
	public Method[] getStaticMethods() {
		List<Method> list = new LinkedList<Method>();
		for (Method m : klass.getMethods()) {
			if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()))
				list.add(m);
		}
		return list.toArray(new Method[list.size()]);
	}

	private static RuntimeException makeSetValueException(	Class<?> type,
															String name,
															Object value,
															Exception e) {
		if (e instanceof FailToSetValueException) {
			return (FailToSetValueException) e;
		}
		return new FailToSetValueException(String.format(	"Fail to set value [%s] to [%s]->[%s] because '%s'",
															value,
															type.getName(),
															name,
															e.getMessage()));
	}

	/**
	 * 为对象的一个字段设值。 不会调用对象的 setter，直接设置字段的值
	 * 
	 * @param obj
	 *            对象
	 * @param field
	 *            字段
	 * @param value
	 *            值。如果为 null，字符和数字字段，都会设成 0
	 * @throws FailToSetValueException
	 */
	public void setValue(Object obj, Field field, Object value) throws FailToSetValueException {
		if (!field.isAccessible())
			field.setAccessible(true);
		Class<?> ft = field.getType();
		// 非 null 值，进行转换
		if (null != value) {
			try {
				value = Castors.me().castTo(value, field.getType());
			}
			catch (FailToCastObjectException e) {
				throw makeSetValueException(obj.getClass(), field.getName(), value, e);
			}
		}
		// 如果是原生类型，转换成默认值
		else if (ft.isPrimitive()) {
			if (boolean.class == ft) {
				value = false;
			} else if (char.class == ft) {
				value = (char) 0;
			} else {
				value = (byte) 0;
			}
		}
		try {
			field.set(obj, value);
		}
		catch (Exception e) {
			throw makeSetValueException(obj.getClass(), field.getName(), value, e);
		}
	}

	/**
	 * 为对象的一个字段设值。优先调用 setter 方法。
	 * 
	 * @param obj
	 *            对象
	 * @param fieldName
	 *            字段名
	 * @param value
	 *            值
	 * @throws FailToSetValueException
	 */
	public void setValue(Object obj, String fieldName, Object value) throws FailToSetValueException {
		if (null == value) {
			try {
				setValue(obj, this.getField(fieldName), value);
			}
			catch (Exception e1) {
				throw makeSetValueException(obj.getClass(), fieldName, value, e1);
			}
		} else {
			try {
				this.getSetter(fieldName, value.getClass()).invoke(obj, value);
			}
			catch (Exception e) {
				try {
					setValue(obj, this.getField(fieldName), value);
				}
				catch (Exception e1) {
					throw makeSetValueException(obj.getClass(), fieldName, value, e1);
				}
			}
		}
	}

	private static RuntimeException makeGetValueException(Class<?> type, String name) {
		return new FailToGetValueException(String.format(	"Fail to get value for [%s]->[%s]",
															type.getName(),
															name));
	}

	/**
	 * 不调用 getter，直接获得字段的值
	 * 
	 * @param obj
	 *            对象
	 * @param f
	 *            字段
	 * @return 字段的值。
	 * @throws FailToGetValueException
	 */
	public Object getValue(Object obj, Field f) throws FailToGetValueException {
		if (!f.isAccessible())
			f.setAccessible(true);
		try {
			return f.get(obj);
		}
		catch (Exception e) {
			throw makeGetValueException(obj.getClass(), f.getName());
		}
	}

	/**
	 * 优先通过 getter 获取字段值，如果没有，则直接获取字段值
	 * 
	 * @param obj
	 *            对象
	 * @param name
	 *            字段名
	 * @return 字段值
	 * @throws FailToGetValueException
	 *             既没发现 getter，又没有字段
	 */
	public Object getValue(Object obj, String name) throws FailToGetValueException {
		try {
			return this.getGetter(name).invoke(obj);
		}
		catch (Exception e) {
			try {
				return getValue(obj, getField(name));
			}
			catch (NoSuchFieldException e1) {
				throw makeGetValueException(obj.getClass(), name);
			}
		}
	}

	/**
	 * @return 对象类型
	 */
	public Class<T> getType() {
		return klass;
	}

	/**
	 * @return 对象提炼类型数组。从对象自身的类型到 Object，中间的继承关系中最有特点的几个类型
	 */
	public Class<?>[] extractTypes() {
		return typeExtractor.extract(this);
	}

	/**
	 * @return 获得外覆类
	 * 
	 * @throws RuntimeException
	 *             如果当前类型不是原生类型，则抛出
	 */
	public Class<?> getWrapperClass() {
		if (!klass.isPrimitive()) {
			if (this.isPrimitiveNumber() || this.is(Boolean.class) || this.is(Character.class))
				return klass;
			throw Lang.makeThrow("Class '%s' should be a primitive class", klass.getName());
		}
		// TODO 用散列能快一点
		if (is(int.class))
			return Integer.class;
		if (is(char.class))
			return Character.class;
		if (is(boolean.class))
			return Boolean.class;
		if (is(long.class))
			return Long.class;
		if (is(float.class))
			return Float.class;
		if (is(byte.class))
			return Byte.class;
		if (is(short.class))
			return Short.class;
		if (is(double.class))
			return Double.class;

		throw Lang.makeThrow("Class [%s] has no wrapper class!", klass.getName());
	}

	/**
	 * @return 获得外覆类，如果没有外覆类，则返回自身的类型
	 */
	public Class<?> getWrapper() {
		if (klass.isPrimitive())
			return getWrapperClass();
		return klass;
	}

	/**
	 * @return 如果当前类为内部类，则返回其外部类。否则返回 null
	 */
	public Class<?> getOuterClass() {
		if (Modifier.isStatic(klass.getModifiers()))
			return null;
		String name = klass.getName();
		int pos = name.lastIndexOf('$');
		if (pos == -1)
			return null;
		name = name.substring(0, pos);
		try {
			return Lang.loadClass(name);
		}
		catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * @param args
	 *            构造函数参数
	 * @return 当前对象的构建方式。
	 * 
	 * @see org.nutz.lang.born.Borning
	 */
	public Borning<T> getBorning(Object... args) {
		return new MirrorBorning<T>(this, args).getBorning();
	}

	/**
	 * 根据构造函数参数，创建一个对象。
	 * 
	 * @param args
	 *            构造函数参数
	 * @return 新对象
	 */
	public T born(Object... args) {
		return new MirrorBorning<T>(this, args).born();
	}

	private static boolean doMatchMethodParamsType(Class<?>[] paramTypes, Class<?>[] methodArgTypes) {
		if (paramTypes.length == 0 && methodArgTypes.length == 0)
			return true;
		if (paramTypes.length == methodArgTypes.length) {
			for (int i = 0; i < paramTypes.length; i++)
				if (!Mirror.me(paramTypes[i]).canCastToDirectly((methodArgTypes[i])))
					return false;
			return true;
		} else if (paramTypes.length + 1 == methodArgTypes.length) {
			if (!methodArgTypes[paramTypes.length].isArray())
				return false;
			for (int i = 0; i < paramTypes.length; i++)
				if (!Mirror.me(paramTypes[i]).canCastToDirectly((methodArgTypes[i])))
					return false;
			return true;
		}
		return false;
	}

	/**
	 * 根据函数名称和参数，返回一个函数调用方式
	 * 
	 * @param methodName
	 *            函数名
	 * @param args
	 *            参数
	 * @return 函数调用方式
	 */
	public Invoking getInvoking(String methodName, Object... args) {
		return new Invoking(klass, methodName, args);
	}

	/**
	 * 根据字段名，得出一个字段注入方式。优先用 Setter
	 * 
	 * @param fieldName
	 *            字段名
	 * @return 注入方式。
	 */
	public Injecting getInjecting(String fieldName) {
		Method[] sss = this.findSetters(fieldName);
		if (sss.length == 1)
			return new InjectBySetter(sss[0]);
		else
			try {
				Field field = this.getField(fieldName);
				try {
					Method setter = this.getSetter(field);
					return new InjectBySetter(setter);
				}
				catch (NoSuchMethodException e) {
					return new InjectByField(field);
				}
			}
			catch (NoSuchFieldException e) {
				throw Lang.wrapThrow(e);
			}
	}

	/**
	 * 调用对象的一个方法
	 * 
	 * @param obj
	 *            对象
	 * @param methodName
	 *            方法名
	 * @param args
	 *            参数
	 * @return 调用结果
	 */
	public Object invoke(Object obj, String methodName, Object... args) {
		return getInvoking(methodName, args).invoke(obj);
	}

	/**
	 * 查找一个方法。匹配的很宽泛
	 * 
	 * @param name
	 *            方法名
	 * @param paramTypes
	 *            参数类型列表
	 * @return 方法
	 * @throws NoSuchMethodException
	 */
	public Method findMethod(String name, Class<?>... paramTypes) throws NoSuchMethodException {
		try {
			return klass.getMethod(name, paramTypes);
		}
		catch (NoSuchMethodException e) {
			for (Method m : klass.getMethods()) {
				if (m.getName().equals(name))
					if (doMatchMethodParamsType(paramTypes, m.getParameterTypes()))
						return m;
			}
		}
		throw new NoSuchMethodException(String.format(	"Fail to find Method %s->%s with params:\n%s",
														klass.getName(),
														name,
														Castors.me().castToString(paramTypes)));
	}

	/**
	 * 根据名称和参数个数，查找一组方法
	 * 
	 * @param name
	 *            方法名
	 * @param argNumber
	 *            参数个数
	 * @return 方法数组
	 */
	public Method[] findMethods(String name, int argNumber) {
		List<Method> methods = new LinkedList<Method>();
		for (Method m : klass.getMethods())
			if (m.getName().equals(name))
				if (argNumber < 0)
					methods.add(m);
				else if (m.getParameterTypes().length == argNumber)
					methods.add(m);
		return methods.toArray(new Method[methods.size()]);
	}

	/**
	 * 根据返回值类型，以及参数类型，查找第一个匹配的方法
	 * 
	 * @param returnType
	 *            返回值类型
	 * @param paramTypes
	 *            参数个数
	 * @return 方法
	 * @throws NoSuchMethodException
	 */
	public Method findMethod(Class<?> returnType, Class<?>... paramTypes)
			throws NoSuchMethodException {
		for (Method m : klass.getMethods()) {
			if (returnType == m.getReturnType())
				if (paramTypes.length == m.getParameterTypes().length) {
					boolean noThisOne = false;
					for (int i = 0; i < paramTypes.length; i++) {
						if (paramTypes[i] != m.getParameterTypes()[i]) {
							noThisOne = true;
							break;
						}
					}
					if (!noThisOne)
						return m;
				}
		}
		throw new NoSuchMethodException(String.format(	"Can not find method in [%s] with return type '%s' and arguemtns \n'%s'!",
														klass.getName(),
														returnType.getName(),
														Castors.me().castToString(paramTypes)));

	}

	/**
	 * 一个方法的参数类型同一个给定的参数数组是否可以匹配
	 * 
	 * @param methodParamTypes
	 *            参数类型列表
	 * @param args
	 *            参数
	 * @return 匹配类型
	 * 
	 * @see org.nutz.lang.MatchType
	 */
	public static MatchType matchParamTypes(Class<?>[] methodParamTypes, Object... args) {
		return matchParamTypes(methodParamTypes, evalToTypes(args));
	}

	/**
	 * 将一组对象，变成一组类型
	 * 
	 * @param args
	 *            对象数组
	 * @return 类型数组
	 */
	public static Class<?>[] evalToTypes(Object... args) {
		Class<?>[] types = new Class[args.length];
		int i = 0;
		for (Object arg : args)
			types[i++] = null == arg ? Object.class : arg.getClass();
		return types;
	}

	static Object evalArgToSameTypeRealArray(Object... args) {
		Object array = evalArgToRealArray(args);
		return array == args ? null : array;
	}

	/**
	 * 将一个 Object[] 数组，变成一个真正的数组 T[]
	 * 
	 * @param args
	 *            数组
	 * @return 新数组
	 */
	public static Object evalArgToRealArray(Object... args) {
		if (null == args || args.length == 0 || null == args[0])
			return null;
		Object re = null;
		/*
		 * Check inside the arguments list, to see if all element is in same
		 * type
		 */
		Class<?> type = null;
		for (Object arg : args) {
			if (null == arg)
				break;
			if (null == type) {
				type = arg.getClass();
				continue;
			}
			if (arg.getClass() != type) {
				type = null;
				break;
			}
		}
		/*
		 * If all argument elements in same type, make a new Array by the Type
		 */
		if (type != null) {
			re = Array.newInstance(type, args.length);
			for (int i = 0; i < args.length; i++) {
				Array.set(re, i, args[i]);
			}
			return re;
		}
		return args;

	}

	/**
	 * 匹配一个函数声明的参数类型数组和一个调用参数数组
	 * 
	 * @param paramTypes
	 *            函数声明参数数组
	 * @param argTypes
	 *            调用参数数组
	 * @return 匹配类型
	 * 
	 * @see org.nutz.lang.MatchType
	 */
	public static MatchType matchParamTypes(Class<?>[] paramTypes, Class<?>[] argTypes) {
		int len = argTypes == null ? 0 : argTypes.length;
		if (len == 0 && paramTypes.length == 0)
			return MatchType.YES;
		if (paramTypes.length == len) {
			for (int i = 0; i < len; i++)
				if (!Mirror.me(argTypes[i]).canCastToDirectly((paramTypes[i])))
					return MatchType.NO;
			return MatchType.YES;
		} else if (len + 1 == paramTypes.length) {
			if (!paramTypes[len].isArray())
				return MatchType.NO;
			for (int i = 0; i < len; i++)
				if (!Mirror.me(argTypes[i]).canCastToDirectly((paramTypes[i])))
					return MatchType.NO;
			return MatchType.LACK;
		}
		return MatchType.NO;
	}

	/**
	 * 判断当前对象是否为一个类型。精确匹配，即使是父类和接口，也不相等
	 * 
	 * @param type
	 *            类型
	 * @return 是否相等
	 */
	public boolean is(Class<?> type) {
		return null != type && klass == type;
	}

	/**
	 * 判断当前对象是否为一个类型。精确匹配，即使是父类和接口，也不相等
	 * 
	 * @param className
	 *            类型名称
	 * @return 是否相等
	 */
	public boolean is(String className) {
		return klass.getName().equals(className);
	}

	/**
	 * @param type
	 *            类型或接口名
	 * @return 当前对象是否为一个类型的子类，或者一个接口的实现类
	 */
	public boolean isOf(Class<?> type) {
		return type.isAssignableFrom(klass);
	}

	/**
	 * @return 当前对象是否为字符串
	 */
	public boolean isString() {
		return is(String.class);
	}

	/**
	 * @return 当前对象是否为CharSequence的子类
	 */
	public boolean isStringLike() {
		return CharSequence.class.isAssignableFrom(klass);
	}

	/**
	 * @return 当前对象是否为字符
	 */
	public boolean isChar() {
		return is(char.class) || is(Character.class);
	}

	/**
	 * @return 当前对象是否为枚举
	 */
	public boolean isEnum() {
		return klass.isEnum();
	}

	/**
	 * @return 当前对象是否为布尔
	 */
	public boolean isBoolean() {
		return is(boolean.class) || is(Boolean.class);
	}

	/**
	 * @return 当前对象是否为浮点
	 */
	public boolean isFloat() {
		return is(float.class) || is(Float.class);
	}

	/**
	 * @return 当前对象是否为双精度浮点
	 */
	public boolean isDouble() {
		return is(double.class) || is(Double.class);
	}

	/**
	 * @return 当前对象是否为整型
	 */
	public boolean isInt() {
		return is(int.class) || is(Integer.class);
	}

	/**
	 * @return 当前对象是否为整数（包括 int, long, short, byte）
	 */
	public boolean isIntLike() {
		return isInt() || isLong() || isShort() || isByte() || is(BigDecimal.class);
	}

	/**
	 * @return 当前对象是否为小数 (float, dobule)
	 */
	public boolean isDecimal() {
		return isFloat() || isDouble();
	}

	/**
	 * @return 当前对象是否为长整型
	 */
	public boolean isLong() {
		return is(long.class) || is(Long.class);
	}

	/**
	 * @return 当前对象是否为短整型
	 */
	public boolean isShort() {
		return is(short.class) || is(Short.class);
	}

	/**
	 * @return 当前对象是否为字节型
	 */
	public boolean isByte() {
		return is(byte.class) || is(Byte.class);
	}

	/**
	 * @param type
	 *            类型
	 * @return 否为一个对象的外覆类
	 */
	public boolean isWrpperOf(Class<?> type) {
		try {
			return Mirror.me(type).getWrapperClass() == klass;
		}
		catch (Exception e) {}
		return false;
	}

	/**
	 * @param type
	 *            目标类型
	 * @return 判断当前对象是否能直接转换到目标类型，而不产生异常
	 */
	public boolean canCastToDirectly(Class<?> type) {
		if (klass == type || type.isAssignableFrom(klass))
			return true;
		if (klass.isPrimitive() && type.isPrimitive()) {
			if (this.isPrimitiveNumber() && Mirror.me(type).isPrimitiveNumber())
				return true;
		}
		try {
			return Mirror.me(type).getWrapperClass() == this.getWrapperClass();
		}
		catch (Exception e) {}
		return false;
	}

	/**
	 * @return 当前对象是否为原生的数字类型 （即不包括 boolean 和 char）
	 */
	public boolean isPrimitiveNumber() {
		return isInt() || isLong() || isFloat() || isDouble() || isByte() || isShort();
	}

	/**
	 * @return 当前对象是否为数字
	 */
	public boolean isNumber() {
		return Number.class.isAssignableFrom(klass)
				|| klass.isPrimitive()
				&& !is(boolean.class)
				&& !is(char.class);
	}

	/**
	 * @return 当前对象是否在表示日期或时间
	 */
	public boolean isDateTimeLike() {
		return Calendar.class.isAssignableFrom(klass)
				|| java.util.Date.class.isAssignableFrom(klass)
				|| java.sql.Date.class.isAssignableFrom(klass)
				|| java.sql.Time.class.isAssignableFrom(klass);
	}

	public String toString() {
		return klass.getName();
	}

	static Object[] blankArrayArg(Class<?>[] pts) {
		return (Object[]) Array.newInstance(pts[pts.length - 1].getComponentType(), 0);
	}

	/**
	 * 获取一个类的泛型参数数组，如果这个类没有泛型参数，返回 null
	 */
	public static Type[] getTypeParams(Class<?> klass) {
		if (klass == null)
			return null;
		Type superclass = klass.getGenericSuperclass();
		if (superclass == null || "java.lang.Object".equals(superclass.toString()))
			return null;
		if (superclass instanceof ParameterizedType)
			return ((ParameterizedType) superclass).getActualTypeArguments();
		return getTypeParams(klass.getSuperclass());
	}

	private static final Pattern PTN = Pattern.compile("(<)(.+)(>)");

	/**
	 * 获取一个字段的泛型参数数组，如果这个字段没有泛型，返回空数组
	 * 
	 * @param f
	 *            字段
	 * @return 泛型参数数组
	 */
	public static Class<?>[] getGenericTypes(Field f) {
		String gts = f.toGenericString();
		Matcher m = PTN.matcher(gts);
		if (m.find()) {
			String s = m.group(2);
			String[] ss = Strings.splitIgnoreBlank(s);
			if (ss.length > 0) {
				Class<?>[] re = new Class<?>[ss.length];
				try {
					for (int i = 0; i < ss.length; i++) {
						String className = ss[i];
						if (className.length() > 0 && className.charAt(0) == '?')
							re[i] = Object.class;
						else {
							int pos = className.indexOf('<');
							if (pos < 0)
								re[i] = Lang.loadClass(className);
							else
								re[i] = Lang.loadClass(className.substring(0, pos));
						}
					}
					return re;
				}
				catch (ClassNotFoundException e) {
					throw Lang.wrapThrow(e);
				}
			}
		}
		return new Class<?>[0];
	}

	/**
	 * 获取一个类的某个一个泛型参数
	 * 
	 * @param klass
	 *            类
	 * @param index
	 *            参数下标 （从 0 开始）
	 * @return 泛型参数类型
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T> getTypeParam(Class<?> klass, int index) {
		Type[] types = getTypeParams(klass);
		if (index >= 0 && index < types.length) {
			Type t = types[index];
			if (t instanceof Class<?>) {
				return (Class<T>) t;
			}
			throw Lang.makeThrow("Type '%s' is not a Class", t.toString());
		}
		throw Lang.makeThrow("Class type param out of range %d/%d", index, types.length);
	}

	/**
	 * @param klass
	 *            类型
	 * @return 一个类型的包路径
	 */
	public static String getPath(Class<?> klass) {
		return klass.getName().replace('.', '/');
	}

	/**
	 * @param parameterTypes
	 *            函数的参数类型数组
	 * @return 参数的描述符
	 */
	public static String getParamDescriptor(Class<?>[] parameterTypes) {
		StringBuilder sb = new StringBuilder();
		sb.append('(');
		for (Class<?> pt : parameterTypes)
			sb.append(getTypeDescriptor(pt));
		sb.append(')');
		String s = sb.toString();
		return s;
	}

	/**
	 * @param method
	 *            方法
	 * @return 这个方法的描述符
	 */
	public static String getMethodDescriptor(Method method) {
		return getParamDescriptor(method.getParameterTypes())
				+ getTypeDescriptor(method.getReturnType());
	}

	/**
	 * @param c
	 *            构造函数
	 * @return 构造函数的描述符
	 */
	public static String getConstructorDescriptor(Constructor<?> c) {
		return getParamDescriptor(c.getParameterTypes()) + "V";
	}

	/**
	 * @param klass
	 *            类型
	 * @return 获得一个类型的描述符
	 */
	public static String getTypeDescriptor(Class<?> klass) {
		if (klass.isPrimitive()) {
			if (klass == void.class)
				return "V";
			else if (klass == int.class)
				return "I";
			else if (klass == long.class)
				return "J";
			else if (klass == byte.class)
				return "B";
			else if (klass == short.class)
				return "S";
			else if (klass == float.class)
				return "F";
			else if (klass == double.class)
				return "D";
			else if (klass == char.class)
				return "C";
			else
				/* if(klass == boolean.class) */
				return "Z";
		}
		StringBuilder sb = new StringBuilder();
		if (klass.isArray()) {
			return sb.append('[').append(getTypeDescriptor(klass.getComponentType())).toString();
		}
		return sb.append('L').append(Mirror.getPath(klass)).append(';').toString();
	}

	/**
	 * 查找包含某一个特殊注解的字段
	 * 
	 * @param type
	 *            类型
	 * @param ann
	 *            注解类型
	 * @return 字段，null 表示没有找到
	 */
	public static Field findField(Class<?> type, Class<? extends Annotation> ann) {
		Mirror<?> mirror = Mirror.me(type);
		for (Field f : mirror.getFields())
			if (f.isAnnotationPresent(ann))
				return f;
		return null;
	}
}
