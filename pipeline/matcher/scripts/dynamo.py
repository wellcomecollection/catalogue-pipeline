from boto3.dynamodb.conditions import Key

components_gsi = "work-sets-index"


def get_table(session, *, index_date):
    dynamodb = session.resource("dynamodb")
    table_name = f"catalogue-{index_date}_works-graph"
    return dynamodb.Table(table_name)


def get_graph_table_row(session, *, index_date, work_id):
    table = get_table(session, index_date=index_date)
    return table.get_item(Key={"id": work_id})["Item"]


def get_graph_component(session, *, index_date, component_id):
    table = get_table(session, index_date=index_date)
    return table.query(
        IndexName=components_gsi,
        KeyConditionExpression=Key("componentId").eq(component_id),
    )["Items"]
