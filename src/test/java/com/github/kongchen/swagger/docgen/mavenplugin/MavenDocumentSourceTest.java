package com.github.kongchen.swagger.docgen.mavenplugin;

import com.github.kongchen.swagger.docgen.AbstractDocumentSource;
import com.github.kongchen.swagger.docgen.GenerateException;
import com.github.kongchen.swagger.docgen.TypeUtils;
import com.github.kongchen.swagger.docgen.mustache.*;
import com.wordnik.swagger.annotations.ApiProperty;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import sample.model.*;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static junit.framework.Assert.*;

/**
 * Created with IntelliJ IDEA.
 * User: kongchen
 * Date: 6/4/13
 */
public class MavenDocumentSourceTest {
    ApiSource apiSource;

    @BeforeClass
    private void prepare() {
        apiSource = new ApiSource();
        apiSource.setApiVersion("1.0");
        apiSource.setBasePath("http://example.com");
        apiSource.setLocations("sample.api.car;sample.api.garage");
        apiSource.setOutputPath("sample.html");
        apiSource.setOutputTemplate("strapdown.html.mustache");
        apiSource.setSwaggerDirectory(null);
    }

    @Test
    public void testWithFormat() throws Exception, GenerateException {
        apiSource.setWithFormatSuffix(true);
        AbstractDocumentSource documentSource = new MavenDocumentSource(apiSource, new SystemStreamLog());
        documentSource.loadDocuments();
        OutputTemplate outputTemplate = new OutputTemplate(documentSource);
        assertEquals(apiSource.getApiVersion(), outputTemplate.getApiVersion());
        assertEquals(apiSource.getBasePath(), outputTemplate.getBasePath());
        assertEquals(3, outputTemplate.getApiDocuments().size());
        for (MustacheDocument doc : outputTemplate.getApiDocuments()) {
            for (MustacheApi api : doc.getApis()) {
                assertTrue(api.getPath().contains("{format}"));
            }
        }
    }

    @Test
    public void test() throws Exception, GenerateException {
        AbstractDocumentSource documentSource = new MavenDocumentSource(apiSource, new SystemStreamLog());
        documentSource.loadDocuments();
        OutputTemplate outputTemplate = new OutputTemplate(documentSource);
        assertEquals(apiSource.getApiVersion(), outputTemplate.getApiVersion());
        assertEquals(apiSource.getBasePath(), outputTemplate.getBasePath());
        assertEquals(3, outputTemplate.getApiDocuments().size());
        for (MustacheDocument doc : outputTemplate.getApiDocuments()) {
            for (MustacheApi api : doc.getApis()) {
                assertFalse(api.getPath().contains("{format}"));
                if (api.getPath().equals("/car/{carId}")) {
                    Assert.assertEquals(api.getOperations().get(0).getParameters().size(), 4);
                    MustacheOperation op = api.getOperations().get(0);

                    Assert.assertEquals("ETag", op.getResponseHeader().getParas().get(0).getName());
                    Assert.assertEquals("carId",
                            op.getRequestPath().getParas().get(0).getName());
                    Assert.assertEquals("e",
                            op.getRequestQuery().getParas().get(0).getName());

                    Assert.assertEquals("Accept",
                            op.getRequestHeader().getParas().get(0).getName());
                    Assert.assertEquals("MediaType",
                            op.getRequestHeader().getParas().get(0).getType());


                }
            }
        }


        assertEquals(8, outputTemplate.getDataTypes().size());
        List<MustacheDataType> typeList = new LinkedList<MustacheDataType>();
        for (MustacheDataType type : outputTemplate.getDataTypes()) {
            typeList.add(type);
        }
        Collections.sort(typeList, new Comparator<MustacheDataType>() {

            @Override
            public int compare(MustacheDataType o1, MustacheDataType o2) {

                return o1.getName().compareTo(o2.getName());
            }
        });
        assertDataTypeInList(typeList, 0, Address.class);
        assertDataTypeInList(typeList, 1, sample.model.Car.class);
        assertDataTypeInList(typeList, 2, Customer.class);
        assertDataTypeInList(typeList, 3, Email.class);
        assertDataTypeInList(typeList, 4, ForGeneric.class);
        assertDataTypeInList(typeList, 5, G1.class);
        assertDataTypeInList(typeList, 6, G2.class);
        assertDataTypeInList(typeList, 7, sample.model.v2.Car.class);
    }

    private void assertDataTypeInList(List<MustacheDataType> typeList, int indexInList,
                                      Class<?> aClass) throws NoSuchMethodException, NoSuchFieldException {
        MustacheDataType dataType = typeList.get(indexInList);
        XmlRootElement root = aClass.getAnnotation(XmlRootElement.class);
        if (root == null) {
            assertEquals(dataType.getName(), aClass.getSimpleName());
        } else {
            assertEquals(dataType.getName(), root.name());
        }

        for (MustacheItem item : dataType.getItems()) {

            String name = item.getName();
            ApiProperty a = null;

            Field f = null;
            try {
                f = aClass.getDeclaredField(name);
                a = f.getAnnotation(ApiProperty.class);
                if (a == null) {
                    a = getApiProperty(aClass, name);
                }
            } catch (NoSuchFieldException e) {
                a = getApiProperty(aClass, name);
            }

            if (a == null) {
                return;
            }
            String type = a.dataType();
            if (type.equals("")) {
                // need to get true data type
                type = getActualDataType(aClass, name);
            }

            assertEquals(a.access(), nullToEmpty(item.getAccess()));
            assertEquals(a.notes(), nullToEmpty(item.getNotes()));
            assertEquals(type, item.getType());
            assertEquals(a.required(), item.isRequired());
            assertEquals(a.value(), nullToEmpty(item.getDescription()));
        }
    }

    private String getActualDataType(Class<?> aClass, String name) throws NoSuchFieldException {
        String t = null;
        Class<?> type = null;
        Field f = null;
        boolean isArray = false;
        ParameterizedType parameterizedType = null;
        for (Method _m : aClass.getMethods()) {
            XmlElement ele = _m.getAnnotation(XmlElement.class);
            if (ele == null) {
                continue;
            }
            if (ele.name().equals(name)) {
                t = ele.type().getSimpleName();
                if (!t.equals("DEFAULT")) {
                    break;
                }
                type = _m.getReturnType();
                Type gType = _m.getGenericReturnType();
                if (gType instanceof ParameterizedType) {
                    parameterizedType = (ParameterizedType) gType;
                }
                break;
            }
        }
        if (type == null && t == null) {
            for (Field _f : aClass.getDeclaredFields()) {
                XmlElement ele = _f.getAnnotation(XmlElement.class);
                if (ele == null) {
                    continue;
                }
                if (ele.name().equals(name)) {
                    type = _f.getType();
                    break;
                }
            }
        }
        if (type == null) {
            f = aClass.getDeclaredField(name);
            type = f.getType();
            if (Collection.class.isAssignableFrom(type)) {
                parameterizedType = (ParameterizedType) f.getGenericType();
            }
        }
        if (parameterizedType != null) {
            Class<?> genericType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
            t = genericType.getSimpleName();
            isArray = true;
        } else {
            t = type.getSimpleName();
        }

        t = toPrimitive(t);
        return isArray ? TypeUtils.AsArrayType(t) : t;
    }

    private String toPrimitive(String type) {
        if (type.equals("Byte")) {
            return "byte";
        }
        if (type.equals("Short")) {
            return "short";
        }
        if (type.equals("Integer")) {
            return "int";
        }
        if (type.equals("Long")) {
            return "long";
        }
        if (type.equals("Float")) {
            return "float";
        }
        if (type.equals("Double")) {
            return "double";
        }
        if (type.equals("Boolean")) {
            return "boolean";
        }
        if (type.equals("Character")) {
            return "char";
        }
        if (type.equals("String")) {
            return "string";
        }
        return type;
    }

    private ApiProperty getApiProperty(Class<?> aClass, String name) {
        ApiProperty a = null;
        for (Field _f : aClass.getDeclaredFields()) {
            XmlElement ele = _f.getAnnotation(XmlElement.class);
            if (ele == null) {
                continue;
            }
            if (ele.name().equals(name)) {
                a = _f.getAnnotation(ApiProperty.class);
                break;
            }
        }
        for (Method _m : aClass.getMethods()) {
            XmlElement ele = _m.getAnnotation(XmlElement.class);
            if (ele == null) {
                continue;
            }
            if (ele.name().equals(name)) {
                a = _m.getAnnotation(ApiProperty.class);
                break;
            }
        }
        return a;
    }

    private String nullToEmpty(String item) {
        return item == null ? "" : item;
    }
}
