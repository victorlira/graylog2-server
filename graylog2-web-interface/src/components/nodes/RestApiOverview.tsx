/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
import React from 'react';
import styled from 'styled-components';

import RelativeTime from 'components/common/RelativeTime';

const StyledDl = styled.dl`
  margin-top: 5px;
  margin-bottom: 0;

  dt {
    float: left;
  }

  dd {
    margin-left: 150px;
  }
`;

type RestApiOverviewProps = {
  node: any;
};

const RestApiOverview = ({
  node,
}: RestApiOverviewProps) => {
  const { transport_address, last_seen } = node;

  return (
    <StyledDl>
      <dt>Transport address:</dt>
      <dd>{transport_address}</dd>
      <dt>Last seen:</dt>
      <dd><RelativeTime dateTime={last_seen} /></dd>
    </StyledDl>
  );
};

export default RestApiOverview;
