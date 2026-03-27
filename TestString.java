import dev.langchain4j.rag.content.injector.DefaultContentInjector;
public class TestString {
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Field f = DefaultContentInjector.class.getDeclaredField("DEFAULT_PROMPT_TEMPLATE");
        f.setAccessible(true);
        dev.langchain4j.model.input.PromptTemplate template = (dev.langchain4j.model.input.PromptTemplate) f.get(null);
        System.out.println("---");
        System.out.println(template.template());
        System.out.println("---");
    }
}
