from catalogue_graph.src.transformers.base_transformer import BaseTransformer


class WeCoConceptsTransformer(BaseTransformer):

    def transform_node(self, data):
        # Implement transformation logic for WeCo concepts here
        transformed_data = {}
        # Example transformation (to be replaced with actual logic)
        for item in data:
            transformed_item = {
                "id": item.get("id"),
                "label": item.get("label"),
                "description": item.get("description"),
                "source": "weco-concepts"
            }
            transformed_data[item.get("id")] = transformed_item
        return transformed_data
